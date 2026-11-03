package dev.benderapps.heartteamprep.ui.interview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.benderapps.heartteamprep.model.ConferenceConversationEntry
import dev.benderapps.heartteamprep.model.ConferenceQuestion
import dev.benderapps.heartteamprep.ui.components.CenteredLoading
import dev.benderapps.heartteamprep.ui.components.HeartTeamScreen
import dev.benderapps.heartteamprep.ui.components.ListErrorState
import dev.benderapps.heartteamprep.ui.components.NeutralBadge
import dev.benderapps.heartteamprep.ui.components.PrimaryButton
import dev.benderapps.heartteamprep.ui.components.polishedCard
import dev.benderapps.heartteamprep.ui.theme.heartTeamPrepColors
import dev.benderapps.heartteamprep.viewmodel.ConferenceInterviewViewModel
import kotlinx.coroutines.delay

/**
 * The one-question-at-a-time follow-up loop. The backend decides what's
 * asked and when it's done; this screen just shows the current question and,
 * once none is pending, hands off via [onReady] (to the heart-team screen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConferenceInterviewScreen(
    viewModel: ConferenceInterviewViewModel,
    onBack: () -> Unit,
    onReady: () -> Unit
) {
    val colors = heartTeamPrepColors()
    var isConfirmingSkip by rememberSaveable { mutableStateOf(false) }
    var isShowingCaseSummary by rememberSaveable { mutableStateOf(false) }
    var hasAutoShownCaseSummary by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(viewModel.isReadyToContinue) {
        if (viewModel.isReadyToContinue) onReady()
    }

    // Show the original case description once, a beat after the first
    // question appears, so the trainee has it in front of them -- especially
    // when resuming from Recent Cases. It stays reachable from the top bar.
    val canAutoShowSummary = viewModel.currentQuestion != null && viewModel.originalNarrative != null
    LaunchedEffect(canAutoShowSummary) {
        if (canAutoShowSummary && !hasAutoShownCaseSummary) {
            hasAutoShownCaseSummary = true
            delay(400)
            isShowingCaseSummary = true
        }
    }

    HeartTeamScreen(
        title = "Follow-Up Question",
        onBack = onBack,
        actions = {
            if (viewModel.originalNarrative != null) {
                IconButton(onClick = { isShowingCaseSummary = true }) {
                    Icon(Icons.Filled.Description, contentDescription = "Case Summary")
                }
            }
        }
    ) { padding ->
        val question = viewModel.currentQuestion
        val loadError = viewModel.errorMessage
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                viewModel.isLoadingCase -> CenteredLoading("Loading your case…")
                question != null -> InterviewContent(viewModel, question, onSkip = { isConfirmingSkip = true })
                loadError != null -> ListErrorState(
                    title = "Couldn't load your case",
                    message = loadError,
                    onRetry = { viewModel.retryLoad() },
                    modifier = Modifier.padding(20.dp)
                )
            }
        }
    }

    if (isShowingCaseSummary) {
        viewModel.originalNarrative?.let { narrative ->
            ModalBottomSheet(onDismissRequest = { isShowingCaseSummary = false }, containerColor = colors.warmBackground) {
                Column(
                    modifier = Modifier
                        .navigationBarsPadding()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text("Case Summary", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "You can reopen this anytime using the icon in the upper-right corner.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.slateText
                    )
                    Text(narrative, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.polishedCard())
                }
            }
        }
    }

    if (isConfirmingSkip) {
        AlertDialog(
            onDismissRequest = { isConfirmingSkip = false },
            title = { Text("Stop Answering Questions?") },
            text = {
                Text(
                    "The heart team's analysis may be less reliable without the information this and any other unanswered questions were asking for. You can still finalize the case, but some sections may rely on assumptions rather than confirmed details."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    isConfirmingSkip = false
                    viewModel.skipRemainingQuestions()
                }) { Text("Stop and Continue", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { isConfirmingSkip = false }) { Text("Keep Answering") } }
        )
    }

    viewModel.phiNoticeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { viewModel.phiNoticeMessage = null },
            title = { Text("Possible Patient Information Removed") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { viewModel.phiNoticeMessage = null }) { Text("OK") } }
        )
    }
}

@Composable
private fun InterviewContent(viewModel: ConferenceInterviewViewModel, question: ConferenceQuestion, onSkip: () -> Unit) {
    val colors = heartTeamPrepColors()
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(question.text, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                NeutralBadge(question.category)
                Text(question.reason, style = MaterialTheme.typography.bodySmall, color = colors.slateText)
            }

            OutlinedTextField(
                value = viewModel.answerText,
                onValueChange = { viewModel.answerText = it },
                placeholder = { Text("Answer as you would explain it out loud…") },
                enabled = !viewModel.isSubmittingAnswer,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface
                ),
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
        }

        Column(
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            viewModel.errorMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
            }

            PrimaryButton(
                text = "Submit Answer",
                onClick = { viewModel.submitAnswer() },
                enabled = viewModel.canSubmitAnswer,
                isLoading = viewModel.isSubmittingAnswer
            )

            if (viewModel.isSkippingRemainingQuestions) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 8.dp), color = colors.slateText, strokeWidth = 2.dp)
            } else {
                TextButton(onClick = onSkip, enabled = !viewModel.isSubmittingAnswer) {
                    Text("Stop Asking Questions", style = MaterialTheme.typography.labelLarge, color = colors.slateText)
                }
            }
        }
    }
}

/** A read-only list of every follow-up question that was actually asked, and how it was answered. */
@Composable
fun ConferenceFollowUpQuestionsScreen(entries: List<ConferenceConversationEntry>, onBack: () -> Unit) {
    val colors = heartTeamPrepColors()
    HeartTeamScreen(title = "Follow-Up Questions", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            entries.forEachIndexed { index, entry ->
                Column(modifier = Modifier.polishedCard(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Q${index + 1}", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.slateText)
                        NeutralBadge(entry.category)
                    }
                    Text(entry.question, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f))
                    Text(entry.answer, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}
