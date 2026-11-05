package com.twinndroid.ui.theme

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedThemeTest {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val colours = Palette(1, 2, 3, 4, 5, 6, 7)

    @Test
    fun aThemeBringsBackTheWholeLook() {
        val saved = ThemeConfig(background = BgAnimation.NONE, fontScale = 1.2f, header = HeaderSettings(pills = true)).savingTheme("Mine", colours)
        val theme = saved.savedThemes.single()

        val later = saved.copy(presetId = "ocean", custom = null, background = BgAnimation.SNOW, fontScale = 1f, header = HeaderSettings(), mode = ThemeMode.DARK, matchIcon = true)
        val used = later.usingTheme(theme)

        assertEquals(colours, used.custom)
        assertEquals(BgAnimation.NONE, used.background)
        assertEquals(1.2f, used.fontScale)
        assertTrue(used.header.pills)
        assertEquals("ocean", used.presetId)
        assertEquals(ThemeMode.DARK, used.mode)
        assertTrue(used.matchIcon)
        assertEquals(listOf(theme), used.savedThemes)
        assertEquals(theme.look, used.lookOnly())
    }

    @Test
    fun savingWithTheSameNameReplaces() {
        val first = ThemeConfig().savingTheme("Mine", colours)
        val second = first.copy(fontScale = 1.1f).savingTheme("MINE", colours)
        assertEquals(1, second.savedThemes.size)
        assertEquals(1.1f, second.savedThemes.single().look.fontScale)
    }

    @Test
    fun savingTurnsOffMaterialYou() {
        val saved = ThemeConfig(dynamicColor = true).savingTheme("Mine", colours)
        assertFalse(saved.dynamicColor)
        assertFalse(saved.savedThemes.single().look.dynamicColor)
    }

    @Test
    fun survivesBeingStored() {
        val saved = ThemeConfig().savingTheme("One", colours).savingTheme("Two", colours)
        assertEquals(saved, json.decodeFromString<ThemeConfig>(json.encodeToString(saved)))
    }

    @Test
    fun looksStoredBeforeThemesExistedStillLoad() {
        val old = json.decodeFromString<ThemeConfig>("""{"presetId":"ocean","fontScale":1.1}""")
        assertEquals("ocean", old.presetId)
        assertTrue(old.savedThemes.isEmpty())
    }
}
