package tv.own.owntv.core.theme

/**
 * The one place OwnTV's colour values are written down, shared by the TV app and the mobile app.
 *
 * Values are ARGB longs rather than `androidx.compose.ui.graphics.Color` because core carries the
 * Compose *runtime* only — no `compose-ui` — and because the two apps build different colour
 * schemes from them (`androidx.tv.material3` on the TV, `androidx.compose.material3` here). Each
 * app wraps a value once: `Color(OwnTVPalette.DarkBackground)`.
 *
 * DeodatoTV's TV identity is deep navy glass with a controlled cyan neon edge. NEUTRAL and the
 * secondary/tertiary roles are theme-only; the `primary` roles are seeded per [AccentColor]
 * (default teal/cyan).
 *
 * The three `*RailPanel` / `*ContentPanel` / `*PreviewPanel` values are not part of the M3 ladder:
 * they are the shell's per-region colour identity, keeping the navigation, the content and the
 * detail pane distinct in both themes instead of collapsing into the greyer generic elevation
 * steps. Both apps draw those same three regions, so they belong here rather than in either one.
 */
object OwnTVPalette {

    /** Brand mark colour (the DeodatoTV play logo) — constant, and the default cyan neon accent. */
    const val AccentCyan = 0xFF00E5FFL

    // ---------------- DARK (DeodatoTV deep navy over #020a1f) ----------------
    const val DarkBackground = 0xFF020A1FL
    const val DarkSurface = 0xFF04122BL
    const val DarkSurfaceContainerLowest = 0xFF061126L
    const val DarkSurfaceContainerLow = 0xFF071832L
    const val DarkSurfaceContainer = 0xFF082142L
    const val DarkSurfaceContainerHigh = 0xFF0A2B55L
    const val DarkSurfaceContainerHighest = 0xFF103A6DL
    const val DarkRailPanel = 0xFF061B3AL
    const val DarkContentPanel = 0xFF04142CL
    const val DarkPreviewPanel = 0xFF082B55L
    const val DarkOnSurface = 0xFFF7FBFFL
    const val DarkOnSurfaceVariant = 0xFFAFC7D8L
    const val DarkOutline = 0xFF5D8EA5L
    const val DarkOutlineVariant = 0xFF174667L
    const val DarkSecondary = 0xFF8EDCE7L
    const val DarkOnSecondary = 0xFF002F39L
    const val DarkSecondaryContainer = 0xFF094D61L
    const val DarkOnSecondaryContainer = 0xFFD5FAFFL
    const val DarkTertiary = 0xFF7FAEFFL
    const val DarkOnTertiary = 0xFF08275DL
    const val DarkTertiaryContainer = 0xFF143C7FL
    const val DarkOnTertiaryContainer = 0xFFDCE8FFL
    const val DarkError = 0xFFFFB4ABL

    // ---------------- LIGHT (M3 light) ----------------
    const val LightBackground = 0xFFF4FAFFL
    const val LightSurface = 0xFFF4FAFFL
    const val LightSurfaceContainerLowest = 0xFFFFFFFFL
    const val LightSurfaceContainerLow = 0xFFEAF4FCL
    const val LightSurfaceContainer = 0xFFDDEDF8L
    const val LightSurfaceContainerHigh = 0xFFD0E6F4L
    const val LightSurfaceContainerHighest = 0xFFC2DDEEL
    const val LightRailPanel = 0xFFD7EAF6L
    const val LightContentPanel = 0xFFF1F8FCL
    const val LightPreviewPanel = 0xFFCFE6F5L
    const val LightOnSurface = 0xFF071426L
    const val LightOnSurfaceVariant = 0xFF38576BL
    const val LightOutline = 0xFF607D90L
    const val LightOutlineVariant = 0xFFAFC7D8L
    const val LightSecondary = 0xFF266675L
    const val LightOnSecondary = 0xFFFFFFFFL
    const val LightSecondaryContainer = 0xFFC8F4FBL
    const val LightOnSecondaryContainer = 0xFF002026L
    const val LightTertiary = 0xFF365F9DL
    const val LightOnTertiary = 0xFFFFFFFFL
    const val LightTertiaryContainer = 0xFFD7E3FFL
    const val LightOnTertiaryContainer = 0xFF001B3FL
    const val LightError = 0xFFBA1A1AL
}

/**
 * The four M3 primary roles for one accent on one theme, as ARGB longs.
 *
 * They come either from a preset's table ([roles]) or are generated from the user's custom hex
 * seed ([accentRolesFromSeed]).
 */
data class AccentRoleValues(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
)

/**
 * The tonal palette each [AccentColor] seeds the M3 colour scheme with, for both themes (M3 uses
 * lighter tones on dark surfaces, darker tones on light).
 *
 * The display label belongs to whichever app is drawing the settings screen, so it is not here.
 */
private class AccentPalette(val dark: AccentRoleValues, val light: AccentRoleValues)

private val TealPalette = AccentPalette(
    dark = AccentRoleValues(0xFF00E5FFL, 0xFF002A30L, 0xFF005161L, 0xFFC8F7FFL),
    light = AccentRoleValues(0xFF00788AL, 0xFFFFFFFFL, 0xFFC8F7FFL, 0xFF001F26L),
)

private val BluePalette = AccentPalette(
    dark = AccentRoleValues(0xFF7FAEFFL, 0xFF002A66L, 0xFF123F82L, 0xFFD8E3FFL),
    light = AccentRoleValues(0xFF285EADL, 0xFFFFFFFFL, 0xFFD8E3FFL, 0xFF001A41L),
)

private val VioletPalette = AccentPalette(
    dark = AccentRoleValues(0xFFC6B8FFL, 0xFF2D1A72L, 0xFF483891L, 0xFFE6DEFFL),
    light = AccentRoleValues(0xFF6144D0L, 0xFFFFFFFFL, 0xFFE6DEFFL, 0xFF1A0068L),
)

private val GreenPalette = AccentPalette(
    dark = AccentRoleValues(0xFF13D7D7L, 0xFF002F30L, 0xFF005152L, 0xFFC8F7F7L),
    light = AccentRoleValues(0xFF007675L, 0xFFFFFFFFL, 0xFFC8F7F7L, 0xFF002020L),
)

private val AmberPalette = AccentPalette(
    dark = AccentRoleValues(0xFFFFB95CL, 0xFF452B00L, 0xFF624000L, 0xFFFFDDB3L),
    light = AccentRoleValues(0xFF8A5100L, 0xFFFFFFFFL, 0xFFFFDDB3L, 0xFF2C1600L),
)

/** The preset's four primary-role values for the given theme. */
fun AccentColor.roles(isDark: Boolean): AccentRoleValues {
    val palette = when (this) {
        AccentColor.TEAL -> TealPalette
        AccentColor.BLUE -> BluePalette
        AccentColor.VIOLET -> VioletPalette
        AccentColor.GREEN -> GreenPalette
        AccentColor.AMBER -> AmberPalette
    }
    return if (isDark) palette.dark else palette.light
}

/** Parses "#RRGGBB" / "RRGGBB" (also 8-digit AARRGGBB) into an ARGB long; null when invalid. */
fun parseAccentHex(hex: String): Long? {
    val s = hex.trim().removePrefix("#")
    return runCatching {
        when (s.length) {
            6 -> 0xFF000000L or s.toLong(16)
            8 -> s.toLong(16)
            else -> null
        }
    }.getOrNull()
}

/**
 * Generate tonal primary roles from an arbitrary seed colour (the custom hex accent).
 * The seed is used EXACTLY as `primary` so the user's hex renders true; only the supporting
 * contrast roles (onPrimary / containers) are derived by nudging the seed's lightness.
 */
fun accentRolesFromSeed(seed: Long, isDark: Boolean): AccentRoleValues {
    val argb = seed.toInt()
    // Choose a readable foreground for text/icons drawn on top of the exact seed colour.
    val onPrimary = if (androidx.core.graphics.ColorUtils.calculateLuminance(argb) > 0.5) {
        0xFF000000L
    } else {
        0xFFFFFFFFL
    }
    return if (isDark) {
        AccentRoleValues(seed, onPrimary, argb.withLightness(0.26f), argb.withLightness(0.90f))
    } else {
        AccentRoleValues(seed, onPrimary, argb.withLightness(0.88f), argb.withLightness(0.10f))
    }
}

/** Keep the seed's hue/saturation but pin the HSL lightness — a cheap stand-in for M3 tones. */
private fun Int.withLightness(l: Float): Long {
    val hsl = FloatArray(3)
    androidx.core.graphics.ColorUtils.colorToHSL(this, hsl)
    hsl[2] = l
    return androidx.core.graphics.ColorUtils.HSLToColor(hsl).toLong() and 0xFFFFFFFFL
}
