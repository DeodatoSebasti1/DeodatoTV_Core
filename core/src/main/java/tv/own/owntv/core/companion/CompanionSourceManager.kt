package tv.own.owntv.core.companion

import tv.own.owntv.core.model.SourceType

/** Non-sensitive source data that may be displayed by the LAN companion manager. */
data class CompanionSourceSummary(
    val id: Long,
    val name: String,
    val type: SourceType,
    val isDefault: Boolean,
)

enum class CompanionSourceActionKind(val wireName: String) {
    RESYNC("resync"),
    SET_DEFAULT("default"),
    DELETE("delete");

    companion object {
        fun fromWireName(value: String): CompanionSourceActionKind? =
            entries.firstOrNull { it.wireName == value.trim().lowercase() }
    }
}

data class CompanionSourceAction(
    val sourceId: Long,
    val kind: CompanionSourceActionKind,
)

internal fun companionSourceAction(fields: Map<String, String>): CompanionSourceAction? {
    val id = fields["sourceId"]?.toLongOrNull()?.takeIf { it > 0L } ?: return null
    val kind = fields["action"]?.let(CompanionSourceActionKind::fromWireName) ?: return null
    return CompanionSourceAction(id, kind)
}
