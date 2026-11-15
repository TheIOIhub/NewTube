package com.example.newtube.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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
import com.example.newtube.ui.notifications.NotificationsScreen
import com.example.newtube.ui.search.SearchScreen
import com.example.newtube.ui.settings.SettingsScreen
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
        composable("shorts") { ShortsScreen(navController) }
        composable(
            route = "search?focus={focus}",
            arguments = listOf(navArgument("focus") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            SearchScreen(
                navController = navController,
                focus = entry.arguments?.getBoolean("focus") ?: false,
            )
        }
        composable("subscriptions") { SubscriptionsScreen() }
        composable("you") { YouScreen(navController) }
        composable("history") { HistoryScreen(navController) }
        composable("settings") { SettingsScreen(navController) }
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
