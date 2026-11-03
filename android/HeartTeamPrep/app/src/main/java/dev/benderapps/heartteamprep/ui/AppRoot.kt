package dev.benderapps.heartteamprep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.benderapps.heartteamprep.model.AuthenticationState
import dev.benderapps.heartteamprep.ui.auth.WelcomeScreen
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel

/**
 * App root: decides between the Welcome/authentication flow and the
 * signed-in app based on [AuthenticationViewModel.state]. Mirrors the iOS
 * client's `RootView`.
 */
@Composable
fun AppRoot(viewModel: AuthenticationViewModel = viewModel()) {
    val colors = heartTeamPrepColors()

    LaunchedEffect(Unit) { viewModel.refreshSession() }

    when (val state = viewModel.state) {
        is AuthenticationState.CheckingSession, is AuthenticationState.EndingSession -> LoadingScreen(colors)
        is AuthenticationState.SignedOut -> WelcomeScreen(viewModel)
        is AuthenticationState.SignedIn -> ConferenceApp(state.user, viewModel)
    }
}

@Composable
private fun LoadingScreen(colors: HeartTeamPrepColors) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warmBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = colors.primaryText)
    }
}
