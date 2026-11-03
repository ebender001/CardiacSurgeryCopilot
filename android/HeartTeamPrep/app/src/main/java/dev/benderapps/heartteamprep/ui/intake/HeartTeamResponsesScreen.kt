package dev.benderapps.heartteamprep.ui.intake

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.ClassOfRecommendation
import dev.benderapps.heartteamprep.model.ConferenceConversationEntry
import dev.benderapps.heartteamprep.model.HeartTeamResponse
import dev.benderapps.heartteamprep.model.HeartTeamRole
import dev.benderapps.heartteamprep.model.LevelOfEvidence
import dev.benderapps.heartteamprep.ui.components.AnalyzingOverlay
import dev.benderapps.heartteamprep.ui.components.HeartTeamScreen
import dev.benderapps.heartteamprep.ui.components.ListErrorState
import dev.benderapps.heartteamprep.ui.components.SecondaryButton
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.HeartTeamPrepColors
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.HeartTeamResponsesViewModel

private val loadingMessages = listOf(
    "Reviewing the case as a surgeon…",
    "Reviewing the case as a cardiologist…",
    "Weighing a catheter-based approach…"
)

/**
 * Three distinct, in-character heart-team-member takes on the same case. The
 * trainee picks one to read and can switch freely between all three.
 */
@Composable
fun HeartTeamResponsesScreen(
    viewModel: HeartTeamResponsesViewModel,
    onBack: () -> Unit,
    onOpenEvidence: (HeartTeamRole) -> Unit,
    onOpenReport: () -> Unit,
    onOpenFollowUps: (List<ConferenceConversationEntry>) -> Unit
) {
    val colors = heartTeamPrepColors()
    HeartTeamScreen(title = "Heart Team", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("What would the heart team say?", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Three members reviewed this case independently. Choose whose take to read -- you can switch anytime as part of the exercise.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.slateText
                    )
                }

                val responses = viewModel.responses
                val errorMessage = viewModel.errorMessage
                if (responses != null) {
                    FollowUpSummary(viewModel.conversation, colors, onOpenFollowUps)
                    RoleSelector(viewModel.selectedRole, colors) { viewModel.selectedRole = it }
                    ResponseCard(viewModel.selectedRole, responses[viewModel.selectedRole], colors)
                    SecondaryButton(
                        text = "Evidence",
                        onClick = { onOpenEvidence(viewModel.selectedRole) },
                        icon = { Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, tint = colors.primaryText) }
                    )
                    SecondaryButton(
                        text = "View Full Report",
                        onClick = onOpenReport,
                        icon = { Icon(Icons.Filled.Description, contentDescription = null, tint = colors.primaryText) }
                    )
                } else if (errorMessage != null) {
                    ListErrorState(
                        title = "Couldn't load heart team responses",
                        message = errorMessage,
                        onRetry = { viewModel.load() }
                    )
                }
            }

            if (viewModel.isLoading) {
                AnalyzingOverlay(title = "Convening the heart team", messages = loadingMessages)
            }
        }
    }
}

@Composable
private fun FollowUpSummary(
    conversation: List<ConferenceConversationEntry>,
    colors: HeartTeamPrepColors,
    onOpen: (List<ConferenceConversationEntry>) -> Unit
) {
    if (conversation.isEmpty()) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(18.dp))
            Text("No follow-up questions were needed for this case.", style = MaterialTheme.typography.bodySmall, color = colors.slateText)
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { onOpen(conversation) },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(18.dp))
            Text(
                "${conversation.size} follow-up question${if (conversation.size == 1) "" else "s"} asked",
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.slateText.copy(alpha = 0.6f))
        }
    }
}

private fun HeartTeamRole.icon(): ImageVector = when (this) {
    HeartTeamRole.SURGEON -> Icons.Filled.MedicalServices
    HeartTeamRole.NON_INTERVENTIONAL_CARDIOLOGIST -> Icons.Filled.MonitorHeart
    HeartTeamRole.INTERVENTIONAL_CARDIOLOGIST -> Icons.Filled.Bolt
}

@Composable
private fun RoleSelector(selected: HeartTeamRole, colors: HeartTeamPrepColors, onSelect: (HeartTeamRole) -> Unit) {
    // IntrinsicSize.Min + fillMaxHeight keeps all three chips the same height
    // even when one label wraps to a second line.
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        HeartTeamRole.entries.forEach { role ->
            val isSelected = role == selected
            val content = if (isSelected) Color.White else colors.primaryText
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) colors.primary else colors.primaryTint)
                    .clickable { onSelect(role) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(role.icon(), contentDescription = null, tint = content)
                Text(
                    // A zero-width space after the hyphen lets "Non-Interventional" wrap there
                    // instead of mid-word.
                    role.shortName.replace("-", "-\u200B"),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = content,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ResponseCard(role: HeartTeamRole, response: HeartTeamResponse, colors: HeartTeamPrepColors) {
    Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            role.displayName.uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = colors.slateText
        )
        Text(response.recommendation, style = MaterialTheme.typography.titleMedium)

        if (response.classOfRecommendation != null || response.levelOfEvidence != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                response.classOfRecommendation?.let { GradeBadge(it.badgeText, it.color()) }
                response.levelOfEvidence?.let { GradeBadge(it.badgeText, it.color()) }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
        Text(response.rationale, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun GradeBadge(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = Color.White,
        modifier = Modifier
            .clip(CircleShape)
            .background(color)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

/**
 * Standard ACC/AHA guideline colors (green -> yellow -> orange -> red as
 * benefit-to-risk weakens) so a trainee familiar with printed guidelines
 * recognizes them instantly.
 */
private fun ClassOfRecommendation.color(): Color = when (this) {
    ClassOfRecommendation.I -> Color(0xFF4A8C59)
    ClassOfRecommendation.IIA -> Color(0xFFD1A621)
    ClassOfRecommendation.IIB -> Color(0xFFCC7821)
    ClassOfRecommendation.III_NO_BENEFIT, ClassOfRecommendation.III_HARM -> Color(0xFFB33330)
}

/** Darkest blue for the strongest evidence (A), lighter as quality decreases -- same grouping as the printed chart. */
private fun LevelOfEvidence.color(): Color = when (this) {
    LevelOfEvidence.A -> Color(0xFF263D7A)
    LevelOfEvidence.B_RANDOMIZED, LevelOfEvidence.B_NONRANDOMIZED -> Color(0xFF4566B3)
    LevelOfEvidence.C_LIMITED_DATA, LevelOfEvidence.C_EXPERT_OPINION -> Color(0xFF8FA8DB)
}
