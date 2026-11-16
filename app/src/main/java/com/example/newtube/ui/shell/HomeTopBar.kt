package com.example.newtube.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.newtube.R

/** Home header: brand lockup plus refresh, notifications and search. */
@Composable
fun HomeTopBar(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onNotifications: () -> Unit,
    onSearch: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp).weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "NewTube",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        IconButton(onClick = onRefresh, enabled = !isRefreshing) {
            if (isRefreshing) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Filled.Refresh, contentDescription = stringResource(R.string.action_refresh))
            }
        }
        IconButton(onClick = onNotifications) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = stringResource(R.string.action_notifications),
            )
        }
        IconButton(onClick = onSearch) {
            Icon(Icons.Filled.Search, contentDescription = stringResource(R.string.action_search))
        }
    }
}
