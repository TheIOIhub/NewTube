package com.example.newtube.ui.common

import androidx.compose.runtime.compositionLocalOf

/**
 * True while the activity is shown in picture-in-picture: the screen shows only
 * the player, without the surrounding UI, so the PIP window is not a scaled-down
 * copy of the whole watch screen.
 */
val LocalPipMode = compositionLocalOf { false }
