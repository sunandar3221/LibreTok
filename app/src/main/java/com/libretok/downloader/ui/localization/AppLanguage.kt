package com.libretok.downloader.ui.localization

enum class AppLanguage(
    val code: String,
    val nativeName: String,
    val englishName: String
) {
    SYSTEM("system", "Default Sistem", "System Default"),
    INDONESIAN("in", "Bahasa Indonesia", "Indonesian"),
    ENGLISH("en", "English", "English"),
    JAPANESE("ja", "日本語", "Japanese"),
    KOREAN("ko", "한국어", "Korean"),
    RUSSIAN("ru", "Русский", "Russian"),
    SPANISH("es", "Español", "Spanish");

    companion object {
        fun fromCode(code: String): AppLanguage {
            return entries.firstOrNull { it.code.equals(code, ignoreCase = true) }
                ?: if (code.equals("id", ignoreCase = true)) INDONESIAN
                else SYSTEM
        }
    }
}
