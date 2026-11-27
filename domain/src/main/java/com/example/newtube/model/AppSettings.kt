package com.example.newtube.model

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val recordHistory: Boolean = true,
    val useRemoteHistory: Boolean = true,
    val defaultQuality: Int = DEFAULT_QUALITY,
    // Content preferences. ISO codes are validated against the allowlists below.
    val contentCountry: String = "UA",
    val contentLanguage: String = "uk",
    val preferredAudioLanguage: String = "uk",
    val appLanguage: String = "system",
    val preferOriginalAudio: Boolean = true,
    val showSearchSuggestions: Boolean = true,
    val ageRestrictedContent: Boolean = false,
    val safeMode: Boolean = false,
    val showMetadata: Boolean = true,
    // Playback preferences.
    val autoplayNext: Boolean = true,
    val resumePlayback: Boolean = true,
    val doubleTapSeekSeconds: Int = DEFAULT_SEEK_SECONDS,
    val resizeMode: Int = RESIZE_FIT,
    val videoBrightness: Float? = null,
    // Gesture preferences.
    val gestureVolume: Boolean = true,
    val gestureBrightness: Boolean = true,
    val gestureZoom: Boolean = false,
    val gestureSpeed: Boolean = false,
    val volumeGestureSide: GestureSide = GestureSide.RIGHT,
    val brightnessGestureSide: GestureSide = GestureSide.LEFT,
) {
    companion object {
        const val DEFAULT_QUALITY = 720
        const val DEFAULT_SEEK_SECONDS = 10

        // Mirrors androidx.media3.ui.AspectRatioFrameLayout (verified constants).
        const val RESIZE_FIT = 0
        const val RESIZE_FILL = 3
        const val RESIZE_ZOOM = 4

        val QUALITY_OPTIONS = listOf(480, 720, 1080)
        val SEEK_OPTIONS = listOf(5, 10, 15, 30)
        val RESIZE_OPTIONS = listOf(RESIZE_FIT, RESIZE_FILL, RESIZE_ZOOM)
        val VALID_COUNTRIES =
            setOf(
                "UA", "PL", "RO", "CZ", "DE", "GB", "FR", "ES", "IT", "NL",
                "SE", "TR", "US", "CA", "AU", "IN", "JP", "BR",
            )
        val VALID_LANGUAGES =
            setOf("uk", "ru", "en", "pl", "de", "fr", "es", "it", "pt", "tr", "cs", "ro", "ja", "hi")
        val VALID_APP_LANGUAGES = setOf("system", "uk", "en")
    }
}
