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
