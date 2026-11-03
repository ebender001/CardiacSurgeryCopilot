package dev.benderapps.heartteamprep.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parse.Parse
import dev.benderapps.heartteamprep.model.ConferenceCase
import dev.benderapps.heartteamprep.service.BackendError
import dev.benderapps.heartteamprep.service.BackendService
import dev.benderapps.heartteamprep.service.DictationController
import dev.benderapps.heartteamprep.service.DictationVocabulary
import dev.benderapps.heartteamprep.service.OnDeviceSpeechEngine
import dev.benderapps.heartteamprep.service.PHIFilterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Drives the New Case screen. Mirrors the iOS `ConferenceIntakeViewModel` (typed input only for now). */
class ConferenceIntakeViewModel : ViewModel() {
    var narrativeText: String by mutableStateOf("")

    /** Mic dictation into [narrativeText]; the result is appended via [DictationController.onCorrectedText]. */
    val dictation = DictationController(
        engine = OnDeviceSpeechEngine(Parse.getApplicationContext(), DictationVocabulary.contextualStringSeed),
        scope = viewModelScope
    ).also { controller -> controller.onCorrectedText = { narrativeText = it } }
    var isSubmitting: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
    var phiNoticeMessage: String? by mutableStateOf(null)

    /**
     * Enabled as soon as there's any text -- the stricter minimum-length
     * check happens on submit, which surfaces a specific inline message
     * instead of leaving the button disabled with no explanation.
     */
    val canContinue: Boolean
        get() = !isSubmitting && !dictation.isDictating && narrativeText.isNotBlank()

    /**
     * Submits the narrative and returns the created case, or null on
     * failure/redaction (in which case [errorMessage] or [phiNoticeMessage]
     * is set and the text is preserved).
     */
    suspend fun submit(): ConferenceCase? {
        errorMessage = null
        val trimmed = narrativeText.trim()
        if (trimmed.length < MINIMUM_NARRATIVE_LENGTH) {
            errorMessage = "Add a bit more detail before continuing."
            return null
        }

        isSubmitting = true
        try {
            // Final on-device PHI screen before anything is sent to the
            // backend (which forwards the narrative to OpenAI). If anything
            // is found, show the trainee the redacted result instead of
            // silently submitting a rewritten narrative -- they review it
            // and tap Continue again. If the screen itself fails, nothing
            // is sent.
            val phiResult = try {
                withContext(Dispatchers.Default) { PHIFilterService.redact(trimmed) }
            } catch (e: Throwable) {
                errorMessage = "Heart Team Prep couldn't screen your text for patient information, so nothing was sent. Please try again."
                return null
            }
            if (phiResult.hasFindings) {
                narrativeText = phiResult.redactedText
                phiNoticeMessage = phiResult.noticeMessage
                return null
            }

            return BackendService.createConferenceCase(trimmed)
        } catch (e: Exception) {
            errorMessage = (e as? BackendError)?.userMessage ?: "Something went wrong. Please try again."
            return null
        } finally {
            isSubmitting = false
        }
    }

    override fun onCleared() {
        dictation.release()
    }

    private companion object {
        const val MINIMUM_NARRATIVE_LENGTH = 20
    }
}
