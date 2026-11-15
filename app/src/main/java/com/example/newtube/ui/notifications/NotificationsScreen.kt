package com.example.newtube.ui.notifications

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.shell.DetailTopBar

@Composable
fun NotificationsScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.notifications_title),
            onBack = { navController.popBackStack() },
        )
        Text(
            text = stringResource(R.string.notifications_signed_out),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
        )
    }
}
