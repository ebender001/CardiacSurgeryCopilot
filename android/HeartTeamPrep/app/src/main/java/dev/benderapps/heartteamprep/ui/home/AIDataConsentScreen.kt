package dev.benderapps.heartteamprep.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.benderapps.heartteamprep.LegalLinks
import dev.benderapps.heartteamprep.ui.components.PrimaryButton
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors

/**
 * One-time disclosure, shown before the first case, that case text is sent
 * to OpenAI. Copy is grounded in what the client actually does -- see
 * PHIFilterService.
 */
@Composable
fun AIDataConsentScreen(onAgree: () -> Unit, onCancel: () -> Unit) {
    Dialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val colors = heartTeamPrepColors()
        val uriHandler = LocalUriHandler.current

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colors.warmBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(colors.primaryTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.Shield, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(24.dp))
                }

                Text("How Your Case Data Is Used", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))

                Text(
                    "To help you prepare each case, Heart Team Prep sends parts of it to OpenAI, a third-party AI service, to extract case details, generate follow-up questions, build your structured report, and generate the three heart-team perspectives.",
                    style = MaterialTheme.typography.bodyLarge
                )

                Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("What's sent to OpenAI", style = MaterialTheme.typography.titleSmall)
                    DataRow("Your typed case description")
                    DataRow("Your answers to follow-up questions")
                    DataRow("Case details used to generate your report and heart-team perspectives")
                }

                Text(
                    "When you look up literature for a case topic, a short search query derived from that topic -- not your full case narrative -- is also sent to the National Library of Medicine's PubMed service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateText
                )

                Text(
                    "Before anything reaches our servers, Heart Team Prep automatically screens your text on-device and removes what looks like patient or staff names, hospital or institution names, specific dates, and geographic locations. This is a safety net, not full de-identification -- never type real patient or staff identifiers.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.slateText
                )

                TextButton(onClick = { uriHandler.openUri(LegalLinks.privacyPolicy) }) {
                    Text("Read our full Privacy Policy", style = MaterialTheme.typography.labelLarge, color = colors.primaryText)
                }
            }

            Column(
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 8.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                PrimaryButton(text = "I Agree & Continue", onClick = onAgree)
                TextButton(onClick = onCancel) {
                    Text("Not Now", style = MaterialTheme.typography.labelLarge, color = colors.slateText)
                }
            }
        }
    }
}

@Composable
private fun DataRow(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.AutoMirrored.Filled.CallMade,
            contentDescription = null,
            tint = heartTeamPrepColors().primaryText,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp)
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
