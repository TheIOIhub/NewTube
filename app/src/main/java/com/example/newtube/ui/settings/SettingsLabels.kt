package com.example.newtube.ui.settings

import androidx.annotation.StringRes
import com.example.newtube.R
import com.example.newtube.model.ThemeMode

@StringRes
fun ThemeMode.labelRes(): Int =
    when (this) {
        ThemeMode.SYSTEM -> R.string.theme_system
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.LIGHT -> R.string.theme_light
    }

@StringRes
fun qualityLabelRes(maxHeight: Int): Int =
    when (maxHeight) {
        480 -> R.string.quality_480
        1080 -> R.string.quality_1080
        else -> R.string.quality_720
    }
