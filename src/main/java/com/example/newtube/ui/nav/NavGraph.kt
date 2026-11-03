package com.example.newtube.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.newtube.ui.home.HomeScreen
import com.example.newtube.ui.shorts.ShortsScreen
import com.example.newtube.ui.search.SearchScreen
import com.example.newtube.ui.video.PlayerScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") { HomeScreen(navController) }
        composable("shorts") { ShortsScreen(navController) }
        composable("search") { SearchScreen(navController) }
        composable("videoPlayer/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            PlayerScreen(videoId)
        }
    }
}
