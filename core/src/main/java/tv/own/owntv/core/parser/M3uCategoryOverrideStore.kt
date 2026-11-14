package tv.own.owntv.core.parser

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import tv.own.owntv.core.model.MediaType

private val Context.m3uCategoryOverrideStore: DataStore<Preferences> by preferencesDataStore(name = "owntv_m3u_category_overrides")

/**
 * Manual M3U category classification overrides.
 *
 * These are source-wide, not profile customizations: a group-title from one playlist should keep its
 * chosen import type across app restarts and every future resync, without a Room schema change.
 */
class M3uCategoryOverrideStore(private val context: Context) {
    private val key = stringPreferencesKey("category_overrides")

    internal suspend fun overridesForSource(sourceId: Long): M3uCategoryOverrides =
        parse(context.m3uCategoryOverrideStore.data.first()[key]).forSource(sourceId)

    suspend fun setOverride(sourceId: Long, groupTitle: String, type: MediaType?) {
        val group = M3uCategoryOverrides.normalizeGroupTitle(groupTitle) ?: return
        context.m3uCategoryOverrideStore.edit { prefs ->
            val root = parse(prefs[key]).toMutable()
            val perSource = root.getOrPut(sourceId) { LinkedHashMap() }
            val overrideType = type?.toM3uContentType()
            if (overrideType == null) perSource.remove(group) else perSource[group] = overrideType
            if (perSource.isEmpty()) root.remove(sourceId)
            if (root.isEmpty()) prefs.remove(key) else prefs[key] = serialize(root)
        }
    }

    private fun parse(raw: String?): M3uCategoryOverrideSnapshot {
        if (raw.isNullOrBlank()) return M3uCategoryOverrideSnapshot(emptyMap())
        return runCatching {
            val root = JSONObject(raw)
            val out = LinkedHashMap<Long, Map<String, M3uContentType>>()
            root.keys().forEach { sourceKey ->
                val sourceId = sourceKey.toLongOrNull() ?: return@forEach
                val groups = root.optJSONObject(sourceKey) ?: return@forEach
                val mapped = LinkedHashMap<String, M3uContentType>()
                groups.keys().forEach { group ->
                    val type = groups.optString(group).toM3uContentType() ?: return@forEach
                    mapped[group] = type
                }
                if (mapped.isNotEmpty()) out[sourceId] = mapped
            }
            M3uCategoryOverrideSnapshot(out)
        }.getOrDefault(M3uCategoryOverrideSnapshot(emptyMap()))
    }

    private fun serialize(values: Map<Long, Map<String, M3uContentType>>): String =
        JSONObject().apply {
            values.forEach { (sourceId, groups) ->
                put(
                    sourceId.toString(),
                    JSONObject().apply {
                        groups.forEach { (group, type) -> put(group, type.name) }
                    },
                )
            }
        }.toString()
}

internal data class M3uCategoryOverrideSnapshot(
    val values: Map<Long, Map<String, M3uContentType>>,
) {
    fun forSource(sourceId: Long): M3uCategoryOverrides =
        M3uCategoryOverrides(values[sourceId].orEmpty())

    fun toMutable(): MutableMap<Long, MutableMap<String, M3uContentType>> =
        values.mapValuesTo(LinkedHashMap()) { (_, groups) -> LinkedHashMap(groups) }
}

internal data class M3uCategoryOverrides(
    val byNormalizedGroup: Map<String, M3uContentType>,
) {
    fun apply(groupTitle: String?, automatic: M3uContentType): M3uContentType {
        val group = normalizeGroupTitle(groupTitle) ?: return automatic
        return byNormalizedGroup[group] ?: automatic
    }

    companion object {
        fun normalizeGroupTitle(raw: String?): String? =
            raw?.trim()
                ?.replace(Regex("""\s+"""), " ")
                ?.lowercase()
                ?.takeIf { it.isNotEmpty() }
    }
}

private fun MediaType.toM3uContentType(): M3uContentType? = when (this) {
    MediaType.LIVE -> M3uContentType.LIVE
    MediaType.MOVIE -> M3uContentType.MOVIE
    MediaType.SERIES -> M3uContentType.SERIES
    MediaType.EPISODE -> M3uContentType.SERIES
}

private fun String.toM3uContentType(): M3uContentType? =
    M3uContentType.entries.firstOrNull { it.name == this }
