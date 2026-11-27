package com.example.newtube.ui.settings

/** Full Ukrainian names for the ISO codes offered in content settings. */
private val LANGUAGE_NAMES =
    mapOf(
        "uk" to "Українська",
        "ru" to "Російська",
        "en" to "Англійська",
        "pl" to "Польська",
        "de" to "Німецька",
        "fr" to "Французька",
        "es" to "Іспанська",
        "it" to "Італійська",
        "pt" to "Португальська",
        "tr" to "Турецька",
        "cs" to "Чеська",
        "ro" to "Румунська",
        "ja" to "Японська",
        "hi" to "Гінді",
    )

internal fun languageDisplayName(code: String): String = LANGUAGE_NAMES[code] ?: code
