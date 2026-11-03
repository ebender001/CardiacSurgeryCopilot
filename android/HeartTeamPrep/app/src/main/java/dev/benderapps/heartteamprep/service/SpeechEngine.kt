package dev.benderapps.heartteamprep.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * Turns the microphone into text. An interface so [DictationController]'s
 * lifecycle is testable without a device.
 */
interface SpeechEngine {
    /** Why dictation can't work on this device, or `null` if it can. */
    val unavailableReason: String?

    /** Begin listening; keeps listening across pauses until [stop]. All callbacks arrive on the main thread. */
    fun start(listener: Listener)

    /** Stop listening and deliver everything heard so far via [Listener.onFinished]. */
    fun stop()

    fun release()

    interface Listener {
        fun onFinished(transcript: String)
        fun onError(message: String)
    }
}

/**
 * Dictation using Android's ON-DEVICE speech recognizer only. Audio never
 * leaves the phone -- unlike the iOS client, which has to upload (PHI-redacted)
 * audio to Apple's server recognizer for accuracy, there is no network
 * recognition path here at all, so there is no audio to leak. The cost is
 * that it needs Android 13+ and an offline English speech pack; without
 * them dictation is unavailable and the trainee types instead.
 *
 * `SpeechRecognizer` ends each session on a pause, so this restarts it after
 * every result until [stop], stitching the pieces into one transcript.
 */
class OnDeviceSpeechEngine(
    private val context: Context,
    private val biasingStrings: List<String>
) : SpeechEngine {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listener: SpeechEngine.Listener? = null
    private val segments = mutableListOf<String>()
    private var isStopping = false

    override val unavailableReason: String?
        get() = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ->
                "Dictation needs Android 13 or later, because it only uses on-device speech recognition."
            !SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
                "On-device speech recognition isn't available on this device. You can type instead."
            else -> null
        }

    override fun start(listener: SpeechEngine.Listener) {
        release()
        BeepSuppressor.mute()
        this.listener = listener
        segments.clear()
        isStopping = false
        recognizer = SpeechRecognizer.createOnDeviceSpeechRecognizer(context).also {
            it.setRecognitionListener(recognitionListener)
        }
        listen()
    }

    override fun stop() {
        if (recognizer == null) return
        isStopping = true
        recognizer?.stopListening()
        // stopListening normally yields onResults/onError right away; this
        // is a backstop so a swallowed callback can never leave the UI stuck on "Finishing up".
        mainHandler.postDelayed({ if (isStopping && recognizer != null) finish() }, STOP_TIMEOUT_MS)
    }

    override fun release() {
        mainHandler.removeCallbacksAndMessages(null)
        if (recognizer != null) {
            recognizer?.destroy()
            recognizer = null
            BeepSuppressor.restoreSoon()
        }
        listener = null
    }

    private fun listen() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            // Pauses mid-thought are normal when presenting a case -- don't cut off early.
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putStringArrayListExtra(RecognizerIntent.EXTRA_BIASING_STRINGS, ArrayList(biasingStrings))
            }
        }
        recognizer?.startListening(intent)
    }

    private fun finish() {
        val result = segments.joinToString(" ").trim()
        val callback = listener
        release()
        callback?.onFinished(result)
    }

    private fun fail(message: String) {
        val callback = listener
        release()
        callback?.onError(message)
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { segments += it }
            if (isStopping) finish() else listen()
        }

        override fun onError(error: Int) {
            when {
                // Stopped by the trainee: whatever was already heard is the result.
                isStopping -> finish()
                // A pause with no recognizable speech: keep listening.
                error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> listen()
                else -> fail(messageFor(error))
            }
        }

        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun messageFor(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
            "Heart Team Prep needs microphone access to dictate. You can enable this in Settings, or type instead."
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED, SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
            "English on-device speech recognition isn't downloaded on this device. Install the offline English speech pack in your device's speech settings, or type instead."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY ->
            "Speech recognition is busy. Please try again in a moment."
        else -> "Dictation stopped unexpectedly. Please try again, or type instead."
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 4000L
    }
}
