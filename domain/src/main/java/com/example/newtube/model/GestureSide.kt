package com.example.newtube.model

/** Which half of the player a vertical gesture is attached to. */
enum class GestureSide {
    LEFT,
    RIGHT,
    OFF;

    companion object {
        /** Case-sensitive on purpose, matching [com.example.newtube.model.ThemeMode]. */
        fun fromStored(
            value: String?,
            default: GestureSide = RIGHT,
        ): GestureSide = entries.firstOrNull { it.name == value } ?: default
    }
}
