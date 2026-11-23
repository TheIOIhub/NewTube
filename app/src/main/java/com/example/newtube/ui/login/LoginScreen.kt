package com.example.newtube.ui.login

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.example.newtube.R
import com.example.newtube.model.LoginError
import com.example.newtube.model.LoginState
import com.example.newtube.ui.shell.DetailTopBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    navController: NavController,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val copiedMessage = stringResource(R.string.login_copied)

    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state) {
        if (state is LoginState.Success) navController.popBackStack()
    }

    val close = {
        viewModel.cancel()
        navController.popBackStack()
        Unit
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DetailTopBar(
                title = stringResource(R.string.login_title),
                onBack = close,
            )
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                when (val current = state) {
                    LoginState.Requesting -> {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.login_requesting),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                    is LoginState.Waiting -> {
                        WaitingContent(
                            state = current,
                            onOpenSite = {
                                val intent =
                                    Intent(Intent.ACTION_VIEW, Uri.parse(current.verificationUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                context.startActivity(intent)
                            },
                            onCopy = {
                                val clipboard =
                                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(
                                    ClipData.newPlainText(current.userCode, current.userCode),
                                )
                                scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
                            },
                            onCancel = close,
                        )
                    }
                    LoginState.Success -> {
                        Text(
                            text = stringResource(R.string.login_success),
                            style = MaterialTheme.typography.headlineSmall,
                        )
                    }
                    is LoginState.Error -> {
                        Text(
                            text = stringResource(current.reason.messageRes()),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Spacer(Modifier.height(20.dp))
                        Button(onClick = { viewModel.start() }) {
                            Text(stringResource(R.string.login_retry))
                        }
                        TextButton(onClick = close) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
        )
    }
}

@Composable
private fun WaitingContent(
    state: LoginState.Waiting,
    onOpenSite: () -> Unit,
    onCopy: () -> Unit,
    onCancel: () -> Unit,
) {
    val remainingMs = rememberRemainingMs(state.expiresAtMs)

    Text(
        text = state.userCode,
        fontFamily = FontFamily.Monospace,
        fontSize = 44.sp,
        letterSpacing = 6.sp,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(Modifier.height(12.dp))
    Text(
        text = stringResource(R.string.login_waiting_hint),
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
    )
    if (remainingMs > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.login_expires_in, formatRemaining(remainingMs)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    Spacer(Modifier.height(24.dp))
    Button(onClick = onOpenSite, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_open_site))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.login_copy_code))
    }
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onCancel) {
        Text(stringResource(R.string.action_cancel))
    }
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.login_checking),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun rememberRemainingMs(expiresAtMs: Long): Long {
    var remaining by remember(expiresAtMs) {
        mutableStateOf((expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }
    LaunchedEffect(expiresAtMs) {
        while (true) {
            remaining = (expiresAtMs - System.currentTimeMillis()).coerceAtLeast(0L)
            if (remaining == 0L) break
            delay(1_000L)
        }
    }
    return remaining
}

private fun formatRemaining(ms: Long): String {
    val totalSeconds = (ms + 999) / 1_000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun LoginError.messageRes(): Int =
    when (this) {
        LoginError.ACCESS_DENIED -> R.string.login_error_denied
        LoginError.EXPIRED -> R.string.login_error_expired
        LoginError.NETWORK -> R.string.login_error_network
    }
