package tv.own.owntv.core.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class M3uContentClassifierTest {
    private fun entry(
        name: String = "Item",
        url: String = "https://example.test/stream",
        group: String? = null,
        type: String? = null,
        tvgType: String? = null,
        tvgId: String? = null,
        catchup: String? = null,
    ) = M3uEntry(
        name = name,
        streamUrl = url,
        logo = null,
        groupTitle = group,
        tvgId = tvgId,
        tvgChno = null,
        type = type,
        tvgType = tvgType,
        catchup = catchup,
        catchupSource = null,
        catchupDays = null,
    )

    private fun type(vararg entries: M3uEntry) = entries.map { M3uContentClassifier.classify(it).type }

    @Test fun explicitTagsAreNormalizedAndAuthoritative() {
        assertEquals(
            listOf(
                M3uContentType.MOVIE, M3uContentType.MOVIE, M3uContentType.MOVIE, M3uContentType.MOVIE,
                M3uContentType.SERIES, M3uContentType.SERIES, M3uContentType.LIVE,
            ),
            type(
                entry(type = "movie"), entry(type = " Movie "), entry(type = "movies"), entry(type = "vod"),
                entry(tvgType = "SERIES"), entry(type = "serie"), entry(type = "live"),
            ),
        )
        assertEquals(M3uContentType.SERIES, type(entry(type = "series", group = "Movies")).single())
    }

    @Test fun groupsAreClassifiedWithPortugueseVariants() {
        assertEquals(
            listOf(M3uContentType.MOVIE, M3uContentType.MOVIE, M3uContentType.SERIES, M3uContentType.SERIES),
            type(entry(group = "Movies"), entry(group = "Filmes"), entry(group = "Series"), entry(group = "Séries")),
        )
    }

    @Test fun episodePatternsAreStrongSeriesEvidence() {
        assertEquals(
            List(5) { M3uContentType.SERIES },
            type(
                entry(name = "Show S01E05", url = "https://x/file.mp4"),
                entry(name = "Show S01 E05"),
                entry(name = "Show 2x03"),
                entry(name = "Show Season 2 Episode 3"),
                entry(name = "Show Temporada 2 Episódio 3"),
            ),
        )
    }

    @Test fun controlledUrlSegmentsAreStrongEvidence() {
        assertEquals(
            listOf(M3uContentType.MOVIE, M3uContentType.SERIES, M3uContentType.LIVE),
            type(
                entry(url = "https://x/movie/12"),
                entry(url = "https://x/series/12"),
                entry(url = "https://x/live/12"),
            ),
        )
    }

    @Test fun fileExtensionsAreSecondaryAndIgnoreQueryFragmentAndHeaders() {
        val mp4 = entry(url = "https://x/files/title.mp4?token=abc#part|User-Agent=UA")
        val mkv = entry(url = "https://x/title.mkv?token=.m3u8")
        assertEquals(listOf(M3uContentType.MOVIE, M3uContentType.MOVIE), type(mp4, mkv))
        assertEquals("mp4", M3uContentClassifier.classify(mp4).containerExt)
        assertEquals("mkv", M3uContentClassifier.classify(mkv).containerExt)
    }

    @Test fun ambiguousStreamingExtensionsDoNotDecide() {
        assertEquals(
            listOf(M3uContentType.UNKNOWN, M3uContentType.UNKNOWN),
            type(entry(url = "https://x/a.m3u8"), entry(url = "https://x/a.ts")),
        )
    }

    @Test fun strongerEvidenceWinsAndTrueTiesAreUnknown() {
        assertEquals(M3uContentType.SERIES, type(entry(name = "Show S01E05", url = "https://x/file.mp4")).single())
        assertEquals(M3uContentType.SERIES, type(entry(url = "https://x/series/1", group = "Movies")).single())
        assertEquals(M3uContentType.UNKNOWN, type(entry(url = "https://x/series/movie/1")).single())
        assertEquals(M3uContentType.UNKNOWN, type(entry(group = "Live Action Movies")).single())
    }

    @Test fun catchupIsLiveButTvgIdAloneIsUnknown() {
        assertEquals(M3uContentType.LIVE, type(entry(catchup = "append")).single())
        assertEquals(M3uContentType.UNKNOWN, type(entry(tvgId = "epg-1")).single())
    }

    @Test fun fullLiveAndMixedPlaylistsClassifyIndependently() = runBlocking {
        fun parse(text: String): List<M3uContentType> {
            val out = mutableListOf<M3uContentType>()
            runBlocking {
                M3uParser().parse(text.byteInputStream()) { out += M3uContentClassifier.classify(it).type }
            }
            return out
        }
        val live = parse("""#EXTM3U
#EXTINF:-1 group-title="Live",One
https://x/live/1.m3u8
#EXTINF:-1 group-title="Channels",Two
https://x/2.ts""")
        assertEquals(listOf(M3uContentType.LIVE, M3uContentType.LIVE), live)

        val mixed = parse("""#EXTM3U
#EXTINF:-1 group-title="Live",News
https://x/live/1
#EXTINF:-1 group-title="Filmes",Film
https://x/movie/2.mp4
#EXTINF:-1 group-title="Séries",Show S01E01
https://x/series/3.mp4""")
        assertEquals(listOf(M3uContentType.LIVE, M3uContentType.MOVIE, M3uContentType.SERIES), mixed)
    }

    @Test fun manualCategoryOverridesWinOverAutomaticClassification() {
        val moviesToLive = M3uCategoryOverrides(mapOf("filmes" to M3uContentType.LIVE))
        assertEquals(M3uContentType.LIVE, moviesToLive.apply("Filmes", M3uContentType.MOVIE))

        val seriesToLive = M3uCategoryOverrides(mapOf("séries" to M3uContentType.LIVE))
        assertEquals(M3uContentType.LIVE, seriesToLive.apply("Séries", M3uContentType.SERIES))

        val liveToMovie = M3uCategoryOverrides(mapOf("canais cinema" to M3uContentType.MOVIE))
        assertEquals(M3uContentType.MOVIE, liveToMovie.apply("Canais Cinema", M3uContentType.LIVE))

        val liveToSeries = M3uCategoryOverrides(mapOf("novelas" to M3uContentType.SERIES))
        assertEquals(M3uContentType.SERIES, liveToSeries.apply("Novelas", M3uContentType.LIVE))
    }

    @Test fun manualCategoryOverridesAreSourceScopedAndRemovableByAbsence() {
        val snapshot = M3uCategoryOverrideSnapshot(
            mapOf(
                1L to mapOf("filmes" to M3uContentType.MOVIE),
                2L to mapOf("filmes" to M3uContentType.LIVE),
            ),
        )
        assertEquals(M3uContentType.MOVIE, snapshot.forSource(1L).apply("Filmes", M3uContentType.LIVE))
        assertEquals(M3uContentType.LIVE, snapshot.forSource(2L).apply("Filmes", M3uContentType.MOVIE))
        assertEquals(M3uContentType.SERIES, snapshot.forSource(3L).apply("Filmes", M3uContentType.SERIES))
    }

    @Test fun manualCategoryOverrideNormalizationIsIdempotent() {
        val key = M3uCategoryOverrides.normalizeGroupTitle("  Filmes   HD  ")
        val overrides = M3uCategoryOverrides(mapOf(key!! to M3uContentType.MOVIE))
        assertEquals(M3uContentType.MOVIE, overrides.apply("filmes hd", M3uContentType.LIVE))
        assertEquals(M3uContentType.MOVIE, overrides.apply("Filmes   HD", M3uContentType.LIVE))
    }
}
