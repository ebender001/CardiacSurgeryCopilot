package dev.benderapps.heartteamprep.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepTheme
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel

/**
 * Reached from EmailAuthScreen's "Forgot password?". Uses Parse's existing
 * password-reset email (see `ParseAuthenticationService.sendPasswordReset`)
 * -- there is no separate reset service. Mirrors the iOS client's
 * `PasswordResetView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordResetScreen(viewModel: AuthenticationViewModel, onDismiss: () -> Unit) {
    val colors = heartTeamPrepColors()

    LaunchedEffect(Unit) { viewModel.resetPasswordResetForm() }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing),
        containerColor = colors.warmBackground,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = if (viewModel.resetConfirmationMessage == null) "Cancel" else "Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.warmBackground)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Forgot password?", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Enter your account email and we'll send reset instructions.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.slateText
                )
            }

            val confirmation = viewModel.resetConfirmationMessage
            if (confirmation != null) {
                ConfirmationCard(confirmation, colors)
            } else {
                FormCard(viewModel, colors)
            }
        }
    }
}

@Composable
private fun FormCard(viewModel: AuthenticationViewModel, colors: HeartTeamPrepColors) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Email address", style = MaterialTheme.typography.labelMedium, color = colors.slateText)
            OutlinedTextField(
                value = viewModel.resetEmail,
                onValueChange = { viewModel.resetEmail = it },
                placeholder = { Text("you@hospital.edu") },
                singleLine = true,
                isError = viewModel.resetEmailFieldError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { viewModel.sendPasswordReset() }),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth()
            )
            viewModel.resetEmailFieldError?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
            }
        }

        viewModel.resetErrorMessage?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        Button(
            onClick = { viewModel.sendPasswordReset() },
            enabled = !viewModel.isSendingResetLink,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            if (viewModel.isSendingResetLink) {
                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
            } else {
                Text("Send Reset Link", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ConfirmationCard(message: String, colors: HeartTeamPrepColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primaryText)
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}

@androidx.compose.ui.tooling.preview.Preview(showBackground = true)
@Composable
private fun PasswordResetScreenPreview() {
    HeartTeamPrepTheme {
        PasswordResetScreen(viewModel = AuthenticationViewModel(), onDismiss = {})
    }
}
