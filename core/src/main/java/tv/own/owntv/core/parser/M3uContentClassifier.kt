package tv.own.owntv.core.parser

/** Import-only classification. It deliberately does not extend the persisted [MediaType] model. */
internal enum class M3uContentType { LIVE, MOVIE, SERIES, UNKNOWN }

internal data class M3uClassification(
    val type: M3uContentType,
    /** File extension without the dot, taken from the URL path only. */
    val containerExt: String?,
)

/**
 * Conservative, deterministic classifier for M3U entries.
 *
 * Explicit tags are authoritative. Inferred evidence is scored; an exact top-score tie is UNKNOWN.
 * Weak tvg-id evidence never reaches the minimum confidence by itself.
 */
internal object M3uContentClassifier {
    private const val EPISODE_SCORE = 100
    private const val CATCHUP_SCORE = 95
    private const val URL_SCORE = 80
    private const val GROUP_SCORE = 50
    private const val EXTENSION_SCORE = 25
    private const val TVG_ID_SCORE = 10
    private const val MIN_CONFIDENCE = EXTENSION_SCORE

    private val seriesGroups = setOf(
        "series", "serie", "séries", "shows", "tv shows", "episodes", "episódios",
        "season", "seasons", "temporada", "temporadas",
    )
    private val movieGroups = setOf("movie", "movies", "film", "films", "filme", "filmes", "cinema", "vod")
    private val liveGroups = setOf("live", "channels", "channel", "canais", "television", "tv ao vivo")
    private val vodExtensions = setOf("mp4", "mkv", "avi", "mov", "webm", "m4v")

    private val episodePatterns = listOf(
        Regex("""(?i)\bS\d{1,2}\s*[.\-_ ]?\s*E\d{1,3}\b"""),
        Regex("""(?i)\b\d{1,2}x\d{1,3}\b"""),
        Regex("""(?i)\bSeason\s+\d{1,2}\s+Episode\s+\d{1,3}\b"""),
        Regex("""(?i)\bTemporada\s+\d{1,2}\s+Epis[oó]dio\s+\d{1,3}\b"""),
    )

    fun classify(entry: M3uEntry): M3uClassification {
        explicit(entry.type)?.let { return M3uClassification(it, extension(entry.streamUrl)) }
        explicit(entry.tvgType)?.let { return M3uClassification(it, extension(entry.streamUrl)) }

        val scores = linkedMapOf(
            M3uContentType.LIVE to 0,
            M3uContentType.MOVIE to 0,
            M3uContentType.SERIES to 0,
        )
        fun add(type: M3uContentType, score: Int) {
            // Confidence tiers are priorities, not votes: two weak hints must never outvote one
            // stronger, semantically-specific signal (for example S01E05 vs group + .mp4).
            scores[type] = maxOf(scores.getValue(type), score)
        }

        if (episodePatterns.any { it.containsMatchIn(entry.name) }) add(M3uContentType.SERIES, EPISODE_SCORE)
        if (entry.catchup != null || entry.catchupSource != null || entry.catchupDays != null) {
            add(M3uContentType.LIVE, CATCHUP_SCORE)
        }

        val path = analysisPath(entry.streamUrl)
        val segments = path.split('/').filter { it.isNotBlank() }.map { it.lowercase() }.toSet()
        if ("series" in segments) add(M3uContentType.SERIES, URL_SCORE)
        if (segments.any { it == "movie" || it == "movies" || it == "vod" }) add(M3uContentType.MOVIE, URL_SCORE)
        if ("live" in segments) add(M3uContentType.LIVE, URL_SCORE)

        groupTypes(entry.groupTitle).forEach { add(it, GROUP_SCORE) }

        val ext = extension(entry.streamUrl)
        if (ext in vodExtensions) add(M3uContentType.MOVIE, EXTENSION_SCORE)
        if (!entry.tvgId.isNullOrBlank()) add(M3uContentType.LIVE, TVG_ID_SCORE)

        val best = scores.maxOf { it.value }
        val winners = scores.filterValues { it == best }.keys
        val result = if (best < MIN_CONFIDENCE || winners.size != 1) M3uContentType.UNKNOWN else winners.single()
        return M3uClassification(result, ext)
    }

    private fun explicit(raw: String?): M3uContentType? = when (raw?.trim()?.lowercase()) {
        "live" -> M3uContentType.LIVE
        "movie", "movies", "vod" -> M3uContentType.MOVIE
        "series", "serie" -> M3uContentType.SERIES
        else -> null
    }

    /**
     * Match complete labels and delimiter-separated components, plus controlled individual tokens.
     * A compound carrying evidence for two types (for example "Live Action Movies") contributes to
     * both and therefore cannot silently become Live merely because it contains that word.
     */
    private fun groupTypes(raw: String?): Set<M3uContentType> {
        val value = raw?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return emptySet()
        val components = value.split(Regex("""\s*[|;/,:>]+\s*""")).filter { it.isNotEmpty() }
        val words = value.split(Regex("""[^\p{L}\p{N}]+""")).filter { it.isNotEmpty() }.toSet()
        val candidates = (components + value).toSet()
        return buildSet {
            if (candidates.any { it in seriesGroups } || words.any { it in seriesGroups }) add(M3uContentType.SERIES)
            if (candidates.any { it in movieGroups } || words.any { it in movieGroups }) add(M3uContentType.MOVIE)
            if (candidates.any { it in liveGroups } || words.any { it in liveGroups }) add(M3uContentType.LIVE)
            if ("tv shows" in value) add(M3uContentType.SERIES)
            if ("tv ao vivo" in value) add(M3uContentType.LIVE)
        }
    }

    private fun extension(url: String): String? {
        val file = analysisPath(url).substringAfterLast('/', "")
        val ext = file.substringAfterLast('.', "").lowercase()
        return ext.takeIf { it in vodExtensions }
    }

    private fun analysisPath(url: String): String {
        val pipe = url.indexOf('|').takeIf { it > 0 && url.substring(it + 1).contains('=') }
        val withoutHeaders = if (pipe == null) url else url.substring(0, pipe)
        val query = withoutHeaders.indexOf('?').let { if (it < 0) withoutHeaders.length else it }
        val fragment = withoutHeaders.indexOf('#').let { if (it < 0) withoutHeaders.length else it }
        return withoutHeaders.substring(0, minOf(query, fragment))
    }
}
