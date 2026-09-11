package dev.benderapps.heartteamprep.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.benderapps.heartteamprep.LegalLinks
import dev.benderapps.heartteamprep.R
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepTheme
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel

/**
 * Shown when there's no signed-in user (see AppRoot). "Continue with
 * Google" uses Credential Manager, which owns presenting the system
 * account picker itself -- this screen only forwards the resulting
 * credential to the view model, never touching Credential Manager or
 * Parse APIs directly.
 */
@Composable
fun WelcomeScreen(viewModel: AuthenticationViewModel) {
    val colors = heartTeamPrepColors()
    var isPresentingEmailAuth by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warmBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(16.dp))

        Header(colors)

        Spacer(Modifier.height(20.dp))

        Text(
            text = "Prepare cardiac surgery cases for the heart team conference, with a structured report and multiple specialty perspectives.",
            style = MaterialTheme.typography.bodyLarge,
            color = colors.slateText,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        Spacer(Modifier.height(32.dp))

        Actions(
            viewModel = viewModel,
            colors = colors,
            onContinueWithEmail = {
                viewModel.resetEmailForm()
                isPresentingEmailAuth = true
            }
        )

        LegalAgreementText(colors)

        Spacer(Modifier.height(32.dp))
    }

    if (isPresentingEmailAuth) {
        Dialog(
            onDismissRequest = { isPresentingEmailAuth = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            EmailAuthScreen(viewModel = viewModel, onDismiss = { isPresentingEmailAuth = false })
        }
    }
}

@Composable
private fun Header(colors: HeartTeamPrepColors) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = "Heart Team Prep",
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center
        )
        Text(
            text = "Conference prep & clinical judgment practice",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.slateText
        )
    }
}

@Composable
private fun Actions(
    viewModel: AuthenticationViewModel,
    colors: HeartTeamPrepColors,
    onContinueWithEmail: () -> Unit
) {
    val context = LocalContext.current

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        GoogleSignInButton(
            isInProgress = viewModel.isGoogleSignInInProgress,
            onClick = { viewModel.signInWithGoogle(context) }
        )

        viewModel.googleSignInErrorMessage?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        OrDivider(colors)

        Button(
            onClick = onContinueWithEmail,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.primary)
        ) {
            Text("Continue with Email", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** Styled per Google's Sign in with Google branding guidelines (light/dark button variants). */
@Composable
private fun GoogleSignInButton(isInProgress: Boolean, onClick: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF131314) else Color.White
    val border = if (isDark) Color(0xFF8E918F) else Color(0xFF747775)
    val textColor = if (isDark) Color(0xFFE3E3E3) else Color(0xFF1F1F1F)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(enabled = !isInProgress, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isInProgress) {
            CircularProgressIndicator(modifier = Modifier.height(20.dp), color = textColor, strokeWidth = 2.dp)
        } else {
            Image(
                painter = painterResource(R.drawable.ic_google_logo),
                contentDescription = null,
                modifier = Modifier.height(18.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.width(10.dp))
            Text(text = "Continue with Google", color = textColor, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun OrDivider(colors: HeartTeamPrepColors) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DividerLine(Modifier.weight(1f))
        Text("OR", style = MaterialTheme.typography.labelMedium, color = colors.slateText)
        DividerLine(Modifier.weight(1f))
    }
}

@Composable
private fun DividerLine(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(1.dp)
            .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.12f))
    )
}

@Composable
private fun LegalAgreementText(colors: HeartTeamPrepColors) {
    val linkStyle = TextLinkStyles(style = SpanStyle(color = colors.primary, fontWeight = FontWeight.Medium))
    val annotated = buildAnnotatedString {
        append("By continuing, you agree to the ")
        withLink(LinkAnnotation.Url(LegalLinks.termsOfUse, linkStyle)) { append("Terms of Use") }
        append(" and ")
        withLink(LinkAnnotation.Url(LegalLinks.privacyPolicy, linkStyle)) { append("Privacy Policy") }
        append(".")
    }

    Text(
        text = annotated,
        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
        color = colors.slateText,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 20.dp)
    )
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    HeartTeamPrepTheme {
        WelcomeScreen(viewModel = AuthenticationViewModel())
    }
}
