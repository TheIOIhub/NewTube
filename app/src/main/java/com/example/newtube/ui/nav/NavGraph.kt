package com.example.newtube.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.newtube.ui.about.AboutScreen
import com.example.newtube.ui.channel.ChannelScreen
import com.example.newtube.ui.comments.CommentsScreen
import com.example.newtube.ui.history.HistoryScreen
import com.example.newtube.ui.home.HomeScreen
import com.example.newtube.ui.login.LoginScreen
import com.example.newtube.ui.notifications.NotificationsScreen
import com.example.newtube.ui.search.SearchScreen
import com.example.newtube.ui.settings.AppearanceSettingsScreen
import com.example.newtube.ui.settings.ContentSettingsScreen
import com.example.newtube.ui.settings.GestureSettingsScreen
import com.example.newtube.ui.settings.SettingsScreen
import com.example.newtube.ui.settings.SettingsViewModel
import com.example.newtube.ui.settings.VideoAudioSettingsScreen
import com.example.newtube.ui.shorts.ShortsScreen
import com.example.newtube.ui.subscriptions.SubscriptionsScreen
import com.example.newtube.ui.video.WatchScreen
import com.example.newtube.ui.you.YouScreen

@Composable
fun NavGraph(navController: NavHostController, modifier: Modifier = Modifier) {
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier,
    ) {
        composable("home") { HomeScreen(navController) }
        composable(
            route = "shorts?videoId={videoId}",
            arguments =
                listOf(
                    navArgument("videoId") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                ),
        ) { entry ->
            ShortsScreen(
                navController = navController,
                startVideoId = entry.arguments?.getString("videoId"),
            )
        }
        composable(
            route = "search?focus={focus}",
            arguments =
                listOf(
                    navArgument("focus") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
        ) { entry ->
            SearchScreen(
                navController = navController,
                focus = entry.arguments?.getBoolean("focus") ?: false,
            )
        }
        composable("subscriptions") { SubscriptionsScreen(navController) }
        composable("you") { YouScreen(navController) }
        composable("history") { HistoryScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
        composable("settings/appearance") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            AppearanceSettingsScreen(viewModel, settings) { navController.popBackStack() }
        }
        composable("settings/video") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            VideoAudioSettingsScreen(viewModel, settings) { navController.popBackStack() }
        }
        composable("settings/content") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            ContentSettingsScreen(viewModel, settings) { navController.popBackStack() }
        }
        composable("settings/gestures") {
            val viewModel: SettingsViewModel = hiltViewModel()
            val settings by viewModel.settings.collectAsState()
            GestureSettingsScreen(viewModel, settings) { navController.popBackStack() }
        }
        composable("login") { LoginScreen(navController) }
        composable("about") { AboutScreen(navController) }
        composable("notifications") { NotificationsScreen(navController) }
        composable("channel/{channelId}") { backStackEntry ->
            val channelId = backStackEntry.arguments?.getString("channelId") ?: ""
            ChannelScreen(navController, channelId)
        }
        composable("watch/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            WatchScreen(navController, videoId)
        }
        composable("comments/{videoId}") { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            CommentsScreen(navController, videoId)
        }
    }
}
