package dev.benderapps.heartteamprep.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.benderapps.heartteamprep.model.EmailAuthMode
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel

/**
 * The Sign In / Create Account form, embedded directly in WelcomeScreen
 * (no intermediate "Continue with Email" step). A single form with a
 * segmented Sign In / Create Account switch rather than two separate
 * screens, since the fields mostly overlap. Mirrors the iOS client's
 * `EmailAuthenticationView`. Signing in flips `viewModel.state`, which
 * AppRoot observes, so there's no completion callback to wire up.
 */
@Composable
fun EmailAuthForm(viewModel: AuthenticationViewModel, modifier: Modifier = Modifier) {
    val colors = heartTeamPrepColors()
    var isPresentingPasswordReset by remember { mutableStateOf(false) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        ModePicker(viewModel)
        FormCard(viewModel, colors) {
            viewModel.resetPasswordResetForm()
            viewModel.resetEmail = viewModel.email
            isPresentingPasswordReset = true
        }

        viewModel.formErrorMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        SubmitButton(viewModel, colors)
    }

    if (isPresentingPasswordReset) {
        Dialog(
            onDismissRequest = { isPresentingPasswordReset = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            PasswordResetScreen(viewModel = viewModel, onDismiss = { isPresentingPasswordReset = false })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModePicker(viewModel: AuthenticationViewModel) {
    val colors = heartTeamPrepColors()
    val segmentColors = SegmentedButtonDefaults.colors(
        activeContainerColor = colors.primary,
        activeContentColor = Color.White,
        activeBorderColor = colors.primary
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = viewModel.emailMode == EmailAuthMode.SIGN_IN,
            onClick = {
                viewModel.emailMode = EmailAuthMode.SIGN_IN
                viewModel.resetEmailForm()
            },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
            colors = segmentColors
        ) { Text("Sign In") }
        SegmentedButton(
            selected = viewModel.emailMode == EmailAuthMode.CREATE_ACCOUNT,
            onClick = {
                viewModel.emailMode = EmailAuthMode.CREATE_ACCOUNT
                viewModel.resetEmailForm()
            },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
            colors = segmentColors
        ) { Text("Create Account") }
    }
}

@Composable
private fun FormCard(
    viewModel: AuthenticationViewModel,
    colors: HeartTeamPrepColors,
    onForgotPassword: () -> Unit
) {
    val passwordFocus = remember { FocusRequester() }
    val confirmPasswordFocus = remember { FocusRequester() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(1.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f), RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        LabeledField(title = "Email address", error = viewModel.emailFieldError) {
            OutlinedTextField(
                value = viewModel.email,
                onValueChange = { viewModel.email = it },
                placeholder = { Text("you@hospital.edu") },
                singleLine = true,
                isError = viewModel.emailFieldError != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
        }

        LabeledField(title = "Password", error = viewModel.passwordFieldError) {
            OutlinedTextField(
                value = viewModel.password,
                onValueChange = { viewModel.password = it },
                placeholder = { Text("At least 8 characters") },
                singleLine = true,
                isError = viewModel.passwordFieldError != null,
                visualTransformation = if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (viewModel.emailMode == EmailAuthMode.CREATE_ACCOUNT) ImeAction.Next else ImeAction.Go
                ),
                keyboardActions = KeyboardActions(
                    onNext = { confirmPasswordFocus.requestFocus() },
                    onGo = { viewModel.submitEmailForm {} }
                ),
                trailingIcon = { PasswordVisibilityToggle(viewModel.isPasswordVisible) { viewModel.isPasswordVisible = it } },
                shape = RoundedCornerShape(12.dp),
                colors = fieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
            )
        }

        if (viewModel.emailMode == EmailAuthMode.SIGN_IN) {
            TextButton(onClick = onForgotPassword) {
                Text("Forgot password?", color = colors.primary, style = MaterialTheme.typography.labelLarge)
            }
        }

        if (viewModel.emailMode == EmailAuthMode.CREATE_ACCOUNT) {
            LabeledField(title = "Confirm password", error = viewModel.confirmPasswordFieldError) {
                OutlinedTextField(
                    value = viewModel.confirmPassword,
                    onValueChange = { viewModel.confirmPassword = it },
                    placeholder = { Text("Re-enter password") },
                    singleLine = true,
                    isError = viewModel.confirmPasswordFieldError != null,
                    visualTransformation = if (viewModel.isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = { viewModel.submitEmailForm {} }),
                    trailingIcon = { PasswordVisibilityToggle(viewModel.isPasswordVisible) { viewModel.isPasswordVisible = it } },
                    shape = RoundedCornerShape(12.dp),
                    colors = fieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(confirmPasswordFocus)
                )
            }
        }
    }
}

@Composable
private fun PasswordVisibilityToggle(isVisible: Boolean, onToggle: (Boolean) -> Unit) {
    IconButton(onClick = { onToggle(!isVisible) }) {
        Icon(
            imageVector = if (isVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
            contentDescription = if (isVisible) "Hide password" else "Show password"
        )
    }
}

@Composable
private fun LabeledField(title: String, error: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = heartTeamPrepColors().slateText)
        content()
        error?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedContainerColor = MaterialTheme.colorScheme.surface
)

@Composable
private fun SubmitButton(viewModel: AuthenticationViewModel, colors: HeartTeamPrepColors) {
    Button(
        onClick = { viewModel.submitEmailForm {} },
        enabled = !viewModel.isSubmittingEmailForm,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
    ) {
        if (viewModel.isSubmittingEmailForm) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White, strokeWidth = 2.dp)
        } else {
            Text(
                if (viewModel.emailMode == EmailAuthMode.SIGN_IN) "Sign In" else "Create Account",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

