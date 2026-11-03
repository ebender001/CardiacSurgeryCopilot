package dev.benderapps.heartteamprep.service

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.benderapps.heartteamprep.model.CorrectedDictationSegment
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class DictationPhase { IDLE, LISTENING, FINISHING_UP, CORRECTING }

/**
 * Shared dictation lifecycle: mic capture -> the true final transcript ->
 * on-device PHI screen -> cloud terminology correction -> hand the result
 * back to the owner. Used identically by the case-intake narrative and the
 * interview-answer flows, mirroring the iOS client's `DictationController`.
 *
 * The owner keeps ownership of its own text property and is notified via
 * [onCorrectedText] once a session's result is ready.
 *
 * PHI handling, in order, before anything is sent anywhere: audio never
 * leaves the device (see [OnDeviceSpeechEngine]); the transcript AND the text
 * already in the field are screened on-device; only the screened text goes
 * to the correction call. If screening itself fails, nothing is sent.
 */
class DictationController(
    private val engine: SpeechEngine,
    private val scope: CoroutineScope,
    private val screen: (String) -> PHIFilterResult = PHIFilterService::redact,
    private val correct: suspend (prior: String, segment: String) -> CorrectedDictationSegment = BackendService::correctDictation,
    private val screenDispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    var phase: DictationPhase by mutableStateOf(DictationPhase.IDLE)
        private set
    var errorMessage: String? by mutableStateOf(null)
    var phiNoticeMessage: String? by mutableStateOf(null)

    /** Set by the owner; receives the full text (existing text + this session's result). */
    var onCorrectedText: ((String) -> Unit)? = null

    val isDictating: Boolean get() = phase != DictationPhase.IDLE

    private var textBeforeDictation = ""

    /** Starts dictating if idle, or stops (and processes) if listening. Ignored while finishing/correcting. */
    fun toggle(currentText: String) {
        when (phase) {
            DictationPhase.IDLE -> start(currentText)
            DictationPhase.LISTENING -> {
                phase = DictationPhase.FINISHING_UP
                engine.stop()
            }
            DictationPhase.FINISHING_UP, DictationPhase.CORRECTING -> Unit
        }
    }

    fun reportPermissionDenied() {
        errorMessage = "Heart Team Prep needs microphone access to dictate. You can enable this in Settings, or type instead."
    }

    fun release() = engine.release()

    private fun start(currentText: String) {
        engine.unavailableReason?.let {
            errorMessage = it
            return
        }
        textBeforeDictation = currentText
        phase = DictationPhase.LISTENING
        try {
            engine.start(listener)
        } catch (e: Exception) {
            phase = DictationPhase.IDLE
            errorMessage = "Dictation couldn't start. Please try again, or type instead."
        }
    }

    private val listener = object : SpeechEngine.Listener {
        override fun onFinished(transcript: String) {
            when (phase) {
                DictationPhase.FINISHING_UP -> {
                    phase = DictationPhase.CORRECTING
                    scope.launch { finalize(transcript) }
                }
                // The engine ended on its own rather than via the trainee tapping stop.
                // Salvage what was heard locally -- no cloud call on this path.
                DictationPhase.LISTENING -> scope.launch { salvage(transcript) }
                else -> Unit
            }
        }

        override fun onError(message: String) {
            phase = DictationPhase.IDLE
            errorMessage = message
        }
    }

    private suspend fun finalize(rawSegment: String) {
        if (rawSegment.isBlank()) {
            phase = DictationPhase.IDLE
            return
        }

        // Screen BEFORE anything is sent -- never after. The text already in
        // the field is screened too: it may hold PHI typed by hand, which
        // hasn't passed through the filter yet.
        val prior: PHIFilterResult
        val segment: PHIFilterResult
        try {
            val screened = withContext(screenDispatcher) { screen(textBeforeDictation) to screen(rawSegment) }
            prior = screened.first
            segment = screened.second
        } catch (e: Throwable) {
            // Fail closed: nothing is sent. Keep what was said, locally; it is screened again on submit.
            onCorrectedText?.invoke(merge(textBeforeDictation, rawSegment))
            errorMessage = "Heart Team Prep couldn't screen your dictation for patient information, so medical terms weren't double-checked and nothing was sent."
            phase = DictationPhase.IDLE
            return
        }

        if (prior.hasFindings || segment.hasFindings) {
            phiNoticeMessage = PHIFilterResult("", prior.findings + segment.findings).noticeMessage
        }

        try {
            val result = correct(prior.redactedText, segment.redactedText)
            onCorrectedText?.invoke(merge(prior.redactedText, result.correctedSegment))
        } catch (e: Exception) {
            onCorrectedText?.invoke(merge(prior.redactedText, segment.redactedText))
            errorMessage = fallbackMessage(e)
        }
        phase = DictationPhase.IDLE
    }

    private suspend fun salvage(rawSegment: String) {
        try {
            if (rawSegment.isNotBlank()) {
                val screened = withContext(screenDispatcher) { screen(rawSegment) }
                if (screened.hasFindings) phiNoticeMessage = screened.noticeMessage
                onCorrectedText?.invoke(merge(textBeforeDictation, screened.redactedText))
            }
        } catch (e: Throwable) {
            onCorrectedText?.invoke(merge(textBeforeDictation, rawSegment))
        }
        phase = DictationPhase.IDLE
    }

    private fun fallbackMessage(error: Exception): String {
        val reason = when (error as? BackendError) {
            BackendError.Network -> "Heart Team Prep couldn't reach the server to double-check medical terms."
            BackendError.Decoding -> "Heart Team Prep couldn't read the server's response while checking medical terms."
            BackendError.SessionExpired -> "Your session expired, so medical terms couldn't be double-checked."
            null -> "Heart Team Prep couldn't double-check medical terms right now."
            else -> "The server couldn't check medical terms right now."
        }
        return "$reason Your dictation was added as heard."
    }

    private companion object {
        fun merge(base: String, sessionText: String): String = when {
            sessionText.isEmpty() -> base
            base.isEmpty() -> sessionText
            else -> "$base $sessionText"
        }
    }
}
