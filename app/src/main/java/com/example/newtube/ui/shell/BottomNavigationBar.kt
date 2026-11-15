package com.example.newtube.ui.shell

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.newtube.R

data class BottomNavItem(
    @StringRes val labelRes: Int,
    val route: String,
    @DrawableRes val iconRes: Int,
)

val BOTTOM_NAV_ITEMS =
    listOf(
        BottomNavItem(R.string.tab_home, "home", R.drawable.ic_home),
        BottomNavItem(R.string.tab_shorts, "shorts", R.drawable.ic_shorts),
        BottomNavItem(R.string.tab_search, "search", R.drawable.ic_search),
        BottomNavItem(R.string.tab_subscriptions, "subscriptions", R.drawable.ic_subscriptions),
        BottomNavItem(R.string.tab_you, "you", R.drawable.ic_person),
    )

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val currentRoute =
        navController.currentBackStackEntryAsState().value?.destination?.route?.substringBefore("?")
    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        BOTTOM_NAV_ITEMS.forEach { item ->
            NavigationBarItem(
                icon = {
                    Icon(
                        painter = painterResource(item.iconRes),
                        contentDescription = stringResource(item.labelRes),
                    )
                },
                label = {
                    Text(
                        text = stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Visible,
                    )
                },
                selected = currentRoute == item.route,
                colors =
                    NavigationBarItemDefaults.colors(
                        indicatorColor = Color.Transparent,
                        selectedIconColor = MaterialTheme.colorScheme.onBackground,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                alwaysShowLabel = true,
                onClick = {
                    if (currentRoute != item.route) {
                        navController.navigate(item.route) {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                },
            )
        }
    }
}
