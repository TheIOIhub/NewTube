package com.example.newtube.ui.home

import androidx.annotation.StringRes
import com.example.newtube.R
import com.example.newtube.model.HomeCategory

@StringRes
fun HomeCategory.labelRes(): Int =
    when (this) {
        HomeCategory.FOR_YOU -> R.string.category_for_you
        HomeCategory.ALL -> R.string.category_all
        HomeCategory.MUSIC -> R.string.category_music
        HomeCategory.GAMING -> R.string.category_gaming
        HomeCategory.MOVIES -> R.string.category_movies
        HomeCategory.SPORTS -> R.string.category_sports
        HomeCategory.NEWS -> R.string.category_news
        HomeCategory.TECHNOLOGY -> R.string.category_technology
    }
