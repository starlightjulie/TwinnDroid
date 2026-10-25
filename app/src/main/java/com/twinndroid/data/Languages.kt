package com.twinndroid.data

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

/** A language the app is translated into, shown in its own name. Empty tag = follow the phone. */
class AppLanguage(val tag: String, val nativeName: String)

object Languages {
    val all = listOf(
        AppLanguage("en", "English"),
        AppLanguage("zh-CN", "简体中文"),
        AppLanguage("hi", "हिन्दी"),
        AppLanguage("es", "Español"),
        AppLanguage("fr", "Français"),
        AppLanguage("ar", "العربية"),
        AppLanguage("bn", "বাংলা"),
        AppLanguage("pt-BR", "Português (Brasil)"),
        AppLanguage("ru", "Русский"),
        AppLanguage("ur", "اردو"),
        AppLanguage("id", "Bahasa Indonesia"),
        AppLanguage("de", "Deutsch"),
        AppLanguage("ja", "日本語"),
        AppLanguage("pcm", "Naijá (Nigerian Pidgin)"),
        AppLanguage("ar-EG", "العربية المصرية"),
        AppLanguage("mr", "मराठी"),
        AppLanguage("te", "తెలుగు"),
        AppLanguage("tr", "Türkçe"),
        AppLanguage("ta", "தமிழ்"),
        AppLanguage("zh-HK", "粵語（香港）"),
        AppLanguage("it", "Italiano"),
        AppLanguage("vi", "Tiếng Việt"),
        AppLanguage("ko", "한국어"),
    )

    /** The chosen language tag, or "" when following the system language. */
    fun current(): String = AppCompatDelegate.getApplicationLocales().toLanguageTags().substringBefore(',')

    /** The listed language matching the chosen tag: exact match first, then same base language. */
    fun currentLanguage(): AppLanguage? = current().takeIf { it.isNotEmpty() }?.let { tag ->
        all.firstOrNull { it.tag.equals(tag, true) }
            ?: all.firstOrNull { it.tag.substringBefore('-').equals(tag.substringBefore('-'), true) }
    }

    fun currentName(): String? = currentLanguage()?.nativeName

    /** Switches the app language; the screen recreates itself in the new language. */
    fun apply(tag: String) {
        AppCompatDelegate.setApplicationLocales(
            if (tag.isEmpty()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag),
        )
    }
}
