package com.example.newtube.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.ui.common.EmptyScreen
import com.example.newtube.ui.shell.DetailTopBar

/** Placeholder kept until the Room-backed History screen lands. */
@Composable
fun HistoryScreen(navController: NavController) {
    Column(modifier = Modifier.fillMaxSize()) {
        DetailTopBar(
            title = stringResource(R.string.history_title),
            onBack = { navController.popBackStack() },
        )
        EmptyScreen(stringResource(R.string.history_empty))
    }
}
