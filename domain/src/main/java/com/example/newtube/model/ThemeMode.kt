package com.example.newtube.model

enum class ThemeMode {
    SYSTEM,
    DARK,
    LIGHT,
    ;

    fun resolveDark(systemInDarkTheme: Boolean): Boolean =
        when (this) {
            SYSTEM -> systemInDarkTheme
            DARK -> true
            LIGHT -> false
        }

    companion object {
        /** Unknown or missing values fall back to [SYSTEM]; the name is stored as-is. */
        fun fromStored(value: String?): ThemeMode = entries.firstOrNull { it.name == value } ?: SYSTEM
    }
}
