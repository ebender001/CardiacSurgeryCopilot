package dev.benderapps.heartteamprep.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.benderapps.heartteamprep.model.ConferenceCase
import dev.benderapps.heartteamprep.model.ConferenceCaseStatus
import dev.benderapps.heartteamprep.model.ConferenceQuestion
import dev.benderapps.heartteamprep.service.BackendError
import dev.benderapps.heartteamprep.service.BackendService
import dev.benderapps.heartteamprep.service.PHIFilterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the one-question-at-a-time follow-up loop. The client never decides
 * whether enough information has been collected -- it only displays
 * whatever `status`/`nextQuestion` the backend returns and reacts to it.
 *
 * @param initialCase the snapshot already returned by create/answer when
 *   navigating here right after that call (no extra round trip); `null`
 *   when resuming an in-progress case from Recent Cases.
 */
class ConferenceInterviewViewModel(
    val caseId: String,
    initialCase: ConferenceCase?
) : ViewModel() {
    var status: ConferenceCaseStatus by mutableStateOf(initialCase?.status ?: ConferenceCaseStatus.COLLECTING_INFORMATION)
        private set

    /** The trainee's original description, shown back to them while answering. */
    var originalNarrative: String? by mutableStateOf(initialCase?.originalNarrative)
        private set
    var currentQuestion: ConferenceQuestion? by mutableStateOf(initialCase?.nextQuestion)
        private set
    var answerText: String by mutableStateOf("")
    var isLoadingCase: Boolean by mutableStateOf(initialCase == null)
        private set
    var isSubmittingAnswer: Boolean by mutableStateOf(false)
        private set
    var isSkippingRemainingQuestions: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
    var phiNoticeMessage: String? by mutableStateOf(null)

    /** True once the backend reports no further question is pending -- the screen then moves on. */
    val isReadyToContinue: Boolean
        get() = !isLoadingCase && currentQuestion == null && errorMessage == null

    val canSubmitAnswer: Boolean
        get() = !isSubmittingAnswer && !isSkippingRemainingQuestions && answerText.isNotBlank()

    init {
        if (initialCase == null) loadCase()
    }

    private fun loadCase() {
        viewModelScope.launch {
            try {
                apply(BackendService.getConferenceCase(caseId))
            } catch (e: Exception) {
                errorMessage = message(e)
            }
            isLoadingCase = false
        }
    }

    fun retryLoad() {
        errorMessage = null
        isLoadingCase = true
        loadCase()
    }

    fun submitAnswer() {
        val question = currentQuestion ?: return
        val trimmed = answerText.trim()
        if (trimmed.isEmpty()) {
            errorMessage = "Enter an answer before submitting."
            return
        }

        errorMessage = null
        isSubmittingAnswer = true
        viewModelScope.launch {
            try {
                // Final on-device PHI screen before anything is sent to the
                // backend. If anything is found, show the redacted text for
                // review instead of silently submitting a rewrite.
                val phiResult = try {
                    withContext(Dispatchers.Default) { PHIFilterService.redact(trimmed) }
                } catch (e: Throwable) {
                    errorMessage = "Heart Team Prep couldn't screen your text for patient information, so nothing was sent. Please try again."
                    return@launch
                }
                if (phiResult.hasFindings) {
                    answerText = phiResult.redactedText
                    phiNoticeMessage = phiResult.noticeMessage
                    return@launch
                }

                apply(BackendService.answerConferenceQuestion(caseId, question.id, trimmed))
                answerText = ""
            } catch (e: Exception) {
                // Preserve answerText so the trainee doesn't lose what they typed.
                errorMessage = message(e)
            } finally {
                isSubmittingAnswer = false
            }
        }
    }

    /**
     * Stops the follow-up loop early. The screen must warn the trainee that
     * the analysis may be less reliable before calling this.
     */
    fun skipRemainingQuestions() {
        if (currentQuestion == null) return
        errorMessage = null
        isSkippingRemainingQuestions = true
        viewModelScope.launch {
            try {
                apply(BackendService.skipRemainingConferenceQuestions(caseId))
            } catch (e: Exception) {
                errorMessage = message(e)
            } finally {
                isSkippingRemainingQuestions = false
            }
        }
    }

    private fun apply(result: ConferenceCase) {
        status = result.status
        originalNarrative = result.originalNarrative
        currentQuestion = result.nextQuestion
    }

    private fun message(e: Exception) = (e as? BackendError)?.userMessage ?: "Something went wrong. Please try again."
}
