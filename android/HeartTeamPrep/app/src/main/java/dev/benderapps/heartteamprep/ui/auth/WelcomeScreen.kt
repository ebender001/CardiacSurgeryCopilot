package dev.benderapps.heartteamprep.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import dev.benderapps.heartteamprep.LegalLinks
import dev.benderapps.heartteamprep.R
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepTheme
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.AuthenticationViewModel

// Fixed brand teal / cream (not theme-adaptive): the hero art is a flat
// teal image, so the panel around it must match in light and dark mode,
// the same way the iOS launch splash does.
private val HeroTeal = Color(0xFF0E4F49)
private val HeroCream = Color(0xFFF7F5F0)

/**
 * Shown when there's no signed-in user (see AppRoot). A teal hero with the
 * app's heart-pulse artwork sits above the Sign In / Create Account form
 * (EmailAuthForm); email is the only sign-in method, so there's no
 * intermediate chooser screen.
 */
@Composable
fun WelcomeScreen(viewModel: AuthenticationViewModel) {
    val colors = heartTeamPrepColors()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.warmBackground)
            .verticalScroll(rememberScrollState())
            .imePadding()
    ) {
        Hero()

        Column(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(24.dp))
            EmailAuthForm(viewModel)
            LegalAgreementText(colors)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Hero() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 36.dp, bottomEnd = 36.dp))
            .background(HeroTeal)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 28.dp, end = 28.dp, top = 8.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.pulse_heart),
                contentDescription = null,
                modifier = Modifier.size(150.dp)
            )
            Text(
                text = "Heart Team Prep",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = HeroCream,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Prepare cardiac surgery cases for the heart team conference, with a structured report and multiple specialty perspectives.",
                style = MaterialTheme.typography.bodyMedium,
                color = HeroCream.copy(alpha = 0.78f),
                textAlign = TextAlign.Center
            )
        }
    }
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
