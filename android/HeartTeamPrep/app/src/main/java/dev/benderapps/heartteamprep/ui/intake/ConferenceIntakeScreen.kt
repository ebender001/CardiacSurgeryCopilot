package dev.benderapps.heartteamprep.ui.intake

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.ConferenceCase
import dev.benderapps.heartteamprep.ui.components.AnalyzingOverlay
import dev.benderapps.heartteamprep.ui.components.DictationDialogs
import dev.benderapps.heartteamprep.ui.components.DictationEditor
import dev.benderapps.heartteamprep.ui.components.HeartTeamScreen
import dev.benderapps.heartteamprep.ui.components.PrimaryButton
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.ConferenceIntakeViewModel
import kotlinx.coroutines.launch

private val analyzingMessages = listOf(
    "Reading the case details…",
    "Identifying what's already known…",
    "Checking what the heart team will want to know…"
)

/** The New Case screen: the trainee types a case summary, then continues to follow-up questions. */
@Composable
fun ConferenceIntakeScreen(
    viewModel: ConferenceIntakeViewModel,
    onBack: () -> Unit,
    onCaseCreated: (ConferenceCase) -> Unit
) {
    val colors = heartTeamPrepColors()
    val scope = rememberCoroutineScope()

    HeartTeamScreen(title = "New Case", onBack = onBack) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 20.dp)
                        .padding(top = 8.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Describe the case", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "Present it as you would to the heart team. Do not include protected health information, including patient names, hospital or institution names, dates of service, or geographic locations.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = colors.slateText
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f).polishedCard(),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Dictate or type — whichever is easier.", style = MaterialTheme.typography.bodySmall, color = colors.slateText)
                        DictationEditor(
                            text = viewModel.narrativeText,
                            onTextChange = { viewModel.narrativeText = it },
                            dictation = viewModel.dictation,
                            placeholder = "A 64-year-old man presents with an NSTEMI…",
                            enabled = !viewModel.isSubmitting,
                            modifier = Modifier.fillMaxWidth().weight(1f)
                        )
                    }

                    viewModel.errorMessage?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                PrimaryButton(
                    text = "Continue",
                    onClick = {
                        scope.launch { viewModel.submit()?.let(onCaseCreated) }
                    },
                    enabled = viewModel.canContinue,
                    isLoading = viewModel.isSubmitting,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                )
            }

            if (viewModel.isSubmitting) {
                AnalyzingOverlay(title = "Analyzing your case", messages = analyzingMessages)
            }
        }
    }

    DictationDialogs(viewModel.dictation)

    viewModel.phiNoticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.phiNoticeMessage = null },
            title = { Text("Possible Patient Information Removed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.phiNoticeMessage = null }) { Text("OK") } }
        )
    }
}
