package dev.benderapps.heartteamprep.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.AuthenticatedUser
import dev.benderapps.heartteamprep.model.ConferenceCaseStatus
import dev.benderapps.heartteamprep.model.ConferenceCaseSummary
import dev.benderapps.heartteamprep.ui.components.ListErrorState
import dev.benderapps.heartteamprep.ui.components.NeutralBadge
import dev.benderapps.heartteamprep.ui.components.StatusBadge
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.ConferenceHomeViewModel
import java.text.DateFormat

/**
 * The signed-in landing screen: start a new case, or resume/review a recent
 * one. Mirrors the iOS client's `ConferenceHomeView`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceHomeScreen(
    viewModel: ConferenceHomeViewModel,
    user: AuthenticatedUser,
    onNewCase: () -> Unit,
    onOpenCase: (ConferenceCaseSummary) -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: ((String) -> Unit) -> Unit
) {
    val colors = heartTeamPrepColors()
    var isPresentingAccount by remember { mutableStateOf(false) }

    // Reload every time this screen becomes visible again (not just once),
    // so a case created or progressed elsewhere shows up without a restart.
    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose {}
    }

    Box(modifier = Modifier.fillMaxSize().background(colors.warmBackground)) {
        PullToRefreshBox(
            isRefreshing = viewModel.isLoadingRecentCases && viewModel.recentCases.isNotEmpty(),
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { Header(colors, onAccount = { isPresentingAccount = true }) }

                item {
                    CaseActionCard(
                        icon = Icons.Filled.Mic,
                        title = "Start a New Case",
                        subtitle = "Type a case summary for the heart team conference",
                        colors = colors,
                        onClick = { viewModel.startNewCase(onNewCase) }
                    )
                }

                item { PrivacyReminder(colors) }

                item {
                    Text(
                        "Recent Cases",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }

                val errorMessage = viewModel.recentCasesErrorMessage
                if (viewModel.recentCases.isEmpty() && errorMessage != null) {
                    item {
                        ListErrorState(
                            title = "Couldn't load Recent Cases",
                            message = errorMessage,
                            onRetry = { viewModel.refresh() }
                        )
                    }
                } else if (viewModel.recentCases.isEmpty()) {
                    item {
                        if (viewModel.isLoadingRecentCases) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = colors.primaryText)
                            }
                        } else {
                            EmptyState(colors)
                        }
                    }
                } else {
                    items(viewModel.recentCases, key = { it.id }) { record ->
                        SwipeableCaseRow(
                            record = record,
                            colors = colors,
                            onClick = { onOpenCase(record) },
                            onHide = { viewModel.hideRecentCase(record.id) }
                        )
                    }
                }
            }
        }
    }

    if (viewModel.isPresentingAIConsent) {
        AIDataConsentScreen(
            onAgree = {
                viewModel.recordAIConsent()
                // Consent was the only thing standing in the way -- carry on to
                // the subscription check rather than making them tap again.
                viewModel.startNewCase(onNewCase)
            },
            onCancel = { viewModel.isPresentingAIConsent = false }
        )
    }

    if (viewModel.isPresentingPaywall) {
        PaywallSheet(
            onUnlocked = {
                viewModel.isPresentingPaywall = false
                onNewCase()
            },
            onDismiss = { viewModel.isPresentingPaywall = false }
        )
    }

    if (isPresentingAccount) {
        AccountSheet(
            user = user,
            onDismiss = { isPresentingAccount = false },
            onSignOut = {
                isPresentingAccount = false
                onSignOut()
            },
            onDeleteAccount = onDeleteAccount
        )
    }
}

@Composable
private fun Header(colors: HeartTeamPrepColors, onAccount: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Heart Team Prep", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            Text("Preoperative conference prep", style = MaterialTheme.typography.bodyMedium, color = colors.slateText)
        }
        IconButton(onClick = onAccount) {
            Icon(Icons.Filled.AccountCircle, contentDescription = "Account", modifier = Modifier.size(30.dp), tint = colors.primaryText)
        }
    }
}

@Composable
private fun CaseActionCard(icon: ImageVector, title: String, subtitle: String, colors: HeartTeamPrepColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(colors.primary)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Color.White)
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.8f))
        }
    }
}

/** Reminder under "Start a New Case" to keep the summary de-identified. Quiet by design. */
@Composable
private fun PrivacyReminder(colors: HeartTeamPrepColors) {
    Row(
        modifier = Modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = colors.slateText, modifier = Modifier.size(14.dp))
        Text(
            "Do not include patient identifiers, dates, or locations.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.slateText
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableCaseRow(record: ConferenceCaseSummary, colors: HeartTeamPrepColors, onClick: () -> Unit, onHide: () -> Unit) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onHide()
                true
            } else {
                false
            }
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text("Remove", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.labelLarge)
            }
        }
    ) {
        CaseRow(record, colors, onClick)
    }
}

@Composable
private fun CaseRow(record: ConferenceCaseSummary, colors: HeartTeamPrepColors, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .background(colors.warmBackground, RoundedCornerShape(14.dp))
            .polishedCard(cornerRadius = 14, padding = 14)
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                record.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Medium),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            record.createdAt?.let {
                Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(it), style = MaterialTheme.typography.bodySmall, color = colors.slateText)
            }
        }
        // Deliberately neutral (grays + a touch of teal) -- routine workflow
        // states, not alerts, so nothing here reads as red/urgent.
        when (record.status) {
            ConferenceCaseStatus.COLLECTING_INFORMATION -> NeutralBadge("In Progress")
            ConferenceCaseStatus.READY_TO_FINALIZE -> StatusBadge("Ready", colors.primaryText.copy(alpha = 0.16f), colors.primaryText)
            ConferenceCaseStatus.COMPLETED -> NeutralBadge("Completed")
        }
    }
}

@Composable
private fun EmptyState(colors: HeartTeamPrepColors) {
    val steps = listOf(
        Triple(Icons.Filled.Mic, "Describe the case", "Type the clinical summary."),
        Triple(Icons.Filled.Sms, "Answer focused questions", "Add the details the heart team will ask about."),
        Triple(Icons.Filled.Description, "Review your report", "Diagnosis through postoperative concerns, plus evidence and guidelines.")
    )
    Column(modifier = Modifier.polishedCard(padding = 18), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("No cases yet", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold))
            Text(
                "Start with a brief case summary. Heart Team Prep will help you clarify the details and prepare a structured report for the heart team conference.",
                style = MaterialTheme.typography.bodySmall,
                color = colors.slateText
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            steps.forEachIndexed { index, (icon, title, description) ->
                HowItWorksStep(icon, title, description, colors)
                if (index < steps.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f))
                }
            }
        }
    }
}

@Composable
private fun HowItWorksStep(icon: ImageVector, title: String, description: String, colors: HeartTeamPrepColors) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(28.dp).clip(CircleShape).background(colors.primaryText.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(16.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(description, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
        }
    }
}
