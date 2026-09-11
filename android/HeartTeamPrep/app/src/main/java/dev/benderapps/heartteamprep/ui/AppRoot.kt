package dev.benderapps.heartteamprep.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.benderapps.heartteamprep.model.AuthenticatedUser
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
        is AuthenticationState.SignedIn -> SignedInPlaceholderScreen(state.user, viewModel, colors)
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

/**
 * Placeholder for the signed-in app -- the case-intake/report screens
 * haven't been ported from iOS yet (see android/README.md). Exists so the
 * authentication flow above is reachable and testable end-to-end; account
 * sign-out/deletion here already need to be real since App Store/Play
 * Store review requires in-app account deletion regardless of what else is
 * built.
 */
@Composable
private fun SignedInPlaceholderScreen(
    user: AuthenticatedUser,
    viewModel: AuthenticationViewModel,
    colors: HeartTeamPrepColors
) {
    var isConfirmingDelete by remember { mutableStateOf(false) }
    var deleteErrorMessage by remember { mutableStateOf<String?>(null) }
    var isDeleting by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warmBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Signed in", style = MaterialTheme.typography.headlineSmall)
        Text(
            user.email ?: "No email on file",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.slateText
        )

        Button(
            onClick = { viewModel.signOut() },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text("Sign Out", style = MaterialTheme.typography.titleMedium)
        }

        OutlinedButton(
            onClick = { isConfirmingDelete = true },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("Delete Account", color = MaterialTheme.colorScheme.error)
        }

        deleteErrorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }

    if (isConfirmingDelete) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) isConfirmingDelete = false },
            title = { Text("Delete account?") },
            text = { Text("This permanently deletes your account and all of your case data. This can't be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        isDeleting = true
                        deleteErrorMessage = null
                        viewModel.deleteAccount { message ->
                            isDeleting = false
                            deleteErrorMessage = message
                        }
                        isConfirmingDelete = false
                    },
                    enabled = !isDeleting
                ) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { isConfirmingDelete = false }, enabled = !isDeleting) { Text("Cancel") }
            }
        )
    }
}
