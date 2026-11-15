package com.example.newtube.ui.you

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.common.ChannelAvatar
import com.example.newtube.ui.shell.TabTopBar

@Composable
fun YouScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        TabTopBar(title = stringResource(R.string.tab_you)) {
            IconButton(onClick = { navController.navigate("settings") }) {
                Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.action_settings))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChannelAvatar(url = "", name = stringResource(R.string.you_guest), size = 48.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.you_guest), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.you_guest_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        YouRow(R.drawable.ic_history, stringResource(R.string.you_history)) { navController.navigate("history") }
        YouRow(R.drawable.ic_subscriptions, stringResource(R.string.you_subscriptions)) {
            navController.navigate("subscriptions")
        }
        YouRow(R.drawable.ic_settings, stringResource(R.string.you_settings)) {
            navController.navigate("settings")
        }
        YouRow(R.drawable.ic_info, stringResource(R.string.you_about)) { navController.navigate("about") }
    }
}

@Composable
private fun YouRow(
    iconRes: Int,
    title: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(iconRes), contentDescription = null, modifier = Modifier.width(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Icon(Icons.Filled.KeyboardArrowRight, contentDescription = null)
    }
}
