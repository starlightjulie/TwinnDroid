package com.twinndroid.ui.theme

import com.twinndroid.R
import androidx.annotation.StringRes
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** How the big title card on the Clones tab is drawn. */
@Serializable
enum class HeaderStyle(@StringRes val label: Int) {
    GRADIENT(R.string.hs_gradient),
    GLASS(R.string.hs_glass),
    SOLID(R.string.hs_solid),
    NONE(R.string.none),
}

@Serializable
enum class BgAnimation(@StringRes val label: Int) {
    SPACE(R.string.bg_space),
    PARTICLES(R.string.bg_particles),
    CIRCLES(R.string.bg_circles),
    RINGS(R.string.bg_rings),
    MESH(R.string.bg_mesh),
    SHAPES(R.string.bg_shapes),
    SNOW(R.string.bg_snow),
    GRID(R.string.bg_grid),
    LAVA(R.string.bg_lava),
    AURORA(R.string.bg_aurora),
    WAVES(R.string.bg_waves),
    FIREFLIES(R.string.bg_fireflies),
    BOKEH(R.string.bg_bokeh),
    METEORS(R.string.bg_meteors),
    NONE(R.string.none),
    RANDOM(R.string.bg_random),
}

/** The user-editable colour "aspects" of the app. Values are ARGB. */
@Serializable
data class Palette(
    val primary: Long,
    val secondary: Long,
    val tertiary: Long,
    val background: Long,
    val surface: Long,
    val headerStart: Long,
    val headerEnd: Long,
)

enum class Aspect(@StringRes val label: Int, @StringRes val description: Int) {
    PRIMARY(R.string.aspect_primary, R.string.aspect_primary_desc),
    SECONDARY(R.string.aspect_secondary, R.string.aspect_secondary_desc),
    TERTIARY(R.string.aspect_tertiary, R.string.aspect_tertiary_desc),
    BACKGROUND(R.string.aspect_background, R.string.aspect_background_desc),
    SURFACE(R.string.aspect_surface, R.string.aspect_surface_desc),
    HEADER_START(R.string.aspect_header_start, R.string.aspect_header_start_desc),
    HEADER_END(R.string.aspect_header_end, R.string.aspect_header_end_desc);

    fun get(p: Palette): Long = when (this) {
        PRIMARY -> p.primary
        SECONDARY -> p.secondary
        TERTIARY -> p.tertiary
        BACKGROUND -> p.background
        SURFACE -> p.surface
        HEADER_START -> p.headerStart
        HEADER_END -> p.headerEnd
    }

    fun set(p: Palette, v: Long): Palette = when (this) {
        PRIMARY -> p.copy(primary = v)
        SECONDARY -> p.copy(secondary = v)
        TERTIARY -> p.copy(tertiary = v)
        BACKGROUND -> p.copy(background = v)
        SURFACE -> p.copy(surface = v)
        HEADER_START -> p.copy(headerStart = v)
        HEADER_END -> p.copy(headerEnd = v)
    }
}

/** [icon] is the matching TwinnDroid launcher icon (see AppIcons). */
class ThemePreset(val id: String, val name: String, val dark: Palette, val light: Palette, val icon: String)

object ThemePresets {
    val all = listOf(
        ThemePreset(
            "twinn", "TwinnDroid",
            dark = Palette(0xFFB9A3FF, 0xFF2EE6B6, 0xFFFFB0CB, 0xFF0F0D1A, 0xFF1C1830, 0xFF1E1450, 0xFF7C4DFF),
            light = Palette(0xFF5B30E0, 0xFF006B54, 0xFFB4245E, 0xFFFCF8FF, 0xFFF0EBF8, 0xFF1E1450, 0xFF7C4DFF),
            icon = "Twinn",
        ),
        ThemePreset(
            "midnight", "Midnight AMOLED",
            dark = Palette(0xFF82B1FF, 0xFF64FFDA, 0xFFFF8A80, 0xFF000000, 0xFF121212, 0xFF0D1B3D, 0xFF2962FF),
            light = Palette(0xFF2962FF, 0xFF00897B, 0xFFD32F2F, 0xFFF7F9FF, 0xFFE8EEFB, 0xFF0D1B3D, 0xFF2962FF),
            icon = "Midnight",
        ),
        ThemePreset(
            "ocean", "Ocean",
            dark = Palette(0xFF4FC3F7, 0xFF80CBC4, 0xFFFFCC80, 0xFF06131C, 0xFF10242F, 0xFF01579B, 0xFF00ACC1),
            light = Palette(0xFF0277BD, 0xFF00796B, 0xFFEF6C00, 0xFFF3FAFE, 0xFFE1F1F8, 0xFF01579B, 0xFF00ACC1),
            icon = "Ocean",
        ),
        ThemePreset(
            "forest", "Forest",
            dark = Palette(0xFF81C784, 0xFFDCE775, 0xFFFFB74D, 0xFF0B140C, 0xFF172419, 0xFF1B5E20, 0xFF689F38),
            light = Palette(0xFF2E7D32, 0xFF827717, 0xFFE65100, 0xFFF5FAF3, 0xFFE4EFE1, 0xFF1B5E20, 0xFF689F38),
            icon = "Mint",
        ),
        ThemePreset(
            "sunset", "Sunset",
            dark = Palette(0xFFFF9E80, 0xFFFFD180, 0xFFF48FB1, 0xFF1A0E0E, 0xFF2A1818, 0xFFB71C1C, 0xFFFF6F00),
            light = Palette(0xFFD84315, 0xFFB26A00, 0xFFC2185B, 0xFFFFF8F4, 0xFFFBE9E0, 0xFFB71C1C, 0xFFFF6F00),
            icon = "Sunset",
        ),
        ThemePreset(
            "rose", "Rose",
            dark = Palette(0xFFF8BBD0, 0xFFCE93D8, 0xFFFFE082, 0xFF180C12, 0xFF28161F, 0xFF880E4F, 0xFFEC407A),
            light = Palette(0xFFC2185B, 0xFF7B1FA2, 0xFFB28704, 0xFFFFF7FA, 0xFFFBE4EC, 0xFF880E4F, 0xFFEC407A),
            icon = "Rose",
        ),
        ThemePreset(
            "mono", "High contrast",
            dark = Palette(0xFFFFFFFF, 0xFFBDBDBD, 0xFFFFEB3B, 0xFF000000, 0xFF1A1A1A, 0xFF000000, 0xFF424242),
            light = Palette(0xFF000000, 0xFF424242, 0xFF8D6E00, 0xFFFFFFFF, 0xFFEEEEEE, 0xFF212121, 0xFF616161),
            icon = "Mono",
        ),
        ThemePreset(
            "lavender", "Lavender",
            dark = Palette(0xFFC5C0FF, 0xFF8FD3FF, 0xFFFFB4E1, 0xFF0A0A10, 0xFF16161F, 0xFF1C1A33, 0xFF4B45A8),
            light = Palette(0xFF5B54C9, 0xFF0A6E8A, 0xFFA23A75, 0xFFFBFAFF, 0xFFEEEDFA, 0xFF1C1A33, 0xFF4B45A8),
            icon = "Lavender",
        ),
        ThemePreset(
            "cyberpunk", "Cyberpunk",
            dark = Palette(0xFFFF2BD6, 0xFF00F0FF, 0xFFF9F002, 0xFF07010F, 0xFF160A26, 0xFF3A0CA3, 0xFFFF2BD6),
            light = Palette(0xFFC2009E, 0xFF007C8A, 0xFF8A8200, 0xFFFFF5FD, 0xFFF6E6F7, 0xFF3A0CA3, 0xFFFF2BD6),
            icon = "Cyberpunk",
        ),
        ThemePreset(
            "nord", "Nord",
            dark = Palette(0xFF88C0D0, 0xFFA3BE8C, 0xFFEBCB8B, 0xFF2E3440, 0xFF3B4252, 0xFF3B4252, 0xFF5E81AC),
            light = Palette(0xFF5E81AC, 0xFF4F7A3A, 0xFFA87B12, 0xFFECEFF4, 0xFFE5E9F0, 0xFF3B4252, 0xFF5E81AC),
            icon = "Nord",
        ),
        ThemePreset(
            "dracula", "Dracula",
            dark = Palette(0xFFBD93F9, 0xFF50FA7B, 0xFFFF79C6, 0xFF1E1F29, 0xFF282A36, 0xFF282A36, 0xFF6272A4),
            light = Palette(0xFF7C4DD6, 0xFF1E8F45, 0xFFC2367F, 0xFFF8F8F2, 0xFFECECF4, 0xFF282A36, 0xFF6272A4),
            icon = "Dracula",
        ),
        ThemePreset(
            "matcha", "Matcha",
            dark = Palette(0xFFA8C686, 0xFFE8D8A8, 0xFFF2A07B, 0xFF10140C, 0xFF1C2316, 0xFF2F4A22, 0xFF7A9A55),
            light = Palette(0xFF4E6E31, 0xFF7A6A2E, 0xFFB5532A, 0xFFFBFCF5, 0xFFEDF1E3, 0xFF2F4A22, 0xFF7A9A55),
            icon = "Matcha",
        ),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id } ?: all.first()
}

@Serializable
data class ThemeConfig(
    val presetId: String = "twinn",
    val mode: ThemeMode = ThemeMode.SYSTEM,
    /** Material You: colours from the wallpaper (Android 12+). Overrides preset and custom. */
    val dynamicColor: Boolean = false,
    /** When set, replaces the preset entirely (the palette's own background decides light/dark). */
    val custom: Palette? = null,
    val fontScale: Float = 1f,
    val background: BgAnimation = BgAnimation.SPACE,
    /** Background drifts slightly when the phone is tilted. */
    val parallax: Boolean = true,
    /** True-black background in dark mode (saves battery on OLED). */
    val pureBlack: Boolean = false,
    /** Thin edge around cards, panels and buttons. */
    val outlines: Boolean = true,
    /** Translucent cards that let the background animation show through. */
    val glassCards: Boolean = true,
    /** Switch the TwinnDroid launcher icon along with the theme preset. */
    val matchIcon: Boolean = false,
    val header: HeaderSettings = HeaderSettings(),
) {
    fun palette(systemDark: Boolean): Palette {
        custom?.let { return it }
        val preset = ThemePresets.byId(presetId)
        val dark = when (mode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        return if (dark) preset.dark else preset.light
    }
}

/** The big title card on the Clones tab and the pills around its buttons, title and icon. */
@Serializable
data class HeaderSettings(
    val style: HeaderStyle = HeaderStyle.GRADIENT,
    /** Card opacity, 0..1. */
    val opacity: Float = 1f,
    /** Background blur behind the card in dp (Android 12+). */
    val blur: Float = 18f,
    /** Corner radius in dp. */
    val radius: Float = 32f,
    /** Float the card with margins and round all four corners. */
    val floating: Boolean = false,
    val pills: Boolean = false,
    val pillOpacity: Float = 0.35f,
    val pillBlur: Float = 12f,
    /** 0..50 % of the pill height: 50 = fully round. */
    val pillRadius: Float = 50f,
)

class ThemeRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("theme", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val _config = MutableStateFlow(load())
    val config: StateFlow<ThemeConfig> = _config.asStateFlow()

    private fun load(): ThemeConfig = runCatching {
        prefs.getString("config", null)?.let { json.decodeFromString<ThemeConfig>(it) }
    }.getOrNull() ?: ThemeConfig()

    fun reload() {
        _config.value = load()
    }

    fun update(transform: (ThemeConfig) -> ThemeConfig) {
        val next = transform(_config.value)
        prefs.edit { putString("config", json.encodeToString(next)) }
        _config.value = next
    }

    companion object {
        @Volatile private var instance: ThemeRepository? = null
        fun get(context: Context): ThemeRepository =
            instance ?: synchronized(this) {
                instance ?: ThemeRepository(context.applicationContext).also { instance = it }
            }
    }
}
