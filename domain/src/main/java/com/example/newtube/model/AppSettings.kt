package com.example.newtube.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recordHistory: Boolean = true,
    val useRemoteHistory: Boolean = true,
    val defaultQuality: Int = DEFAULT_QUALITY,
) {
    companion object {
        const val DEFAULT_QUALITY = 720
        val QUALITY_OPTIONS = listOf(480, 720, 1080)
    }
}
