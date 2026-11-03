package dev.benderapps.heartteamprep.service

import dev.benderapps.heartteamprep.model.CorrectedDictationSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeEngine(override val unavailableReason: String? = null) : SpeechEngine {
    var listener: SpeechEngine.Listener? = null
    var startCount = 0
    var stopCount = 0

    override fun start(listener: SpeechEngine.Listener) {
        startCount++
        this.listener = listener
    }

    override fun stop() {
        stopCount++
    }

    override fun release() {}
}

class DictationControllerTest {
    private class Harness(
        val engine: FakeEngine = FakeEngine(),
        screen: (String) -> PHIFilterResult = { PHIFilterResult(it, emptyList()) },
        correct: suspend (String, String) -> CorrectedDictationSegment = { _, segment ->
            CorrectedDictationSegment(segment.uppercase(), emptyList())
        }
    ) {
        val correctCalls = mutableListOf<Pair<String, String>>()
        val delivered = mutableListOf<String>()
        val controller = DictationController(
            engine = engine,
            scope = CoroutineScope(Dispatchers.Unconfined),
            screen = screen,
            correct = { prior, segment ->
                correctCalls += prior to segment
                correct(prior, segment)
            },
            screenDispatcher = Dispatchers.Unconfined
        ).also { c -> c.onCorrectedText = { delivered += it } }

        /** Taps the mic, "speaks", taps again, and lets the engine deliver [transcript]. */
        fun dictate(existing: String, transcript: String) {
            controller.toggle(existing)
            controller.toggle(existing)
            engine.listener!!.onFinished(transcript)
        }
    }

    @Test
    fun happyPathAppendsCorrectedSegmentToExistingText() {
        val h = Harness()
        h.dictate("Prior text.", "severe aortic stenosis")

        assertEquals(listOf("Prior text." to "severe aortic stenosis"), h.correctCalls)
        assertEquals(listOf("Prior text. SEVERE AORTIC STENOSIS"), h.delivered)
        assertEquals(DictationPhase.IDLE, h.controller.phase)
        assertNull(h.controller.errorMessage)
    }

    @Test
    fun phaseMovesThroughListeningAndFinishingUp() {
        val h = Harness()
        h.controller.toggle("")
        assertEquals(DictationPhase.LISTENING, h.controller.phase)
        assertEquals(1, h.engine.startCount)

        h.controller.toggle("")
        assertEquals(DictationPhase.FINISHING_UP, h.controller.phase)
        assertEquals(1, h.engine.stopCount)

        // Further taps while finishing do nothing.
        h.controller.toggle("")
        assertEquals(1, h.engine.stopCount)
        assertEquals(1, h.engine.startCount)
    }

    @Test
    fun screensBothExistingTextAndSegmentBeforeAnythingIsSent() {
        val h = Harness(screen = { text ->
            PHIFilterResult(
                text.replace("Smith", "[name removed]"),
                if ("Smith" in text) listOf(PHIFinding(PHIFinding.Category.NAME, "Smith")) else emptyList()
            )
        })
        h.dictate("Seen by Smith.", "and Smith agreed")

        assertEquals(listOf("Seen by [name removed]." to "and [name removed] agreed"), h.correctCalls)
        assertFalse(h.delivered.single().contains("Smith"))
        assertTrue(h.controller.phiNoticeMessage!!.contains("a patient or staff name"))
    }

    @Test
    fun correctionFailureFallsBackToTheScreenedTextWithAMessage() {
        val h = Harness(correct = { _, _ -> throw BackendError.Network })
        h.dictate("A.", "valve repair")

        assertEquals(listOf("A. valve repair"), h.delivered)
        assertTrue(h.controller.errorMessage!!.contains("couldn't reach the server"))
        assertEquals(DictationPhase.IDLE, h.controller.phase)
    }

    @Test
    fun screeningFailureSendsNothing() {
        val h = Harness(screen = { throw IllegalStateException("models not loaded") })
        h.dictate("A.", "valve repair")

        assertTrue("nothing may be sent when screening fails", h.correctCalls.isEmpty())
        assertEquals(listOf("A. valve repair"), h.delivered)
        assertTrue(h.controller.errorMessage!!.contains("nothing was sent"))
        assertEquals(DictationPhase.IDLE, h.controller.phase)
    }

    @Test
    fun unavailableEngineNeverStarts() {
        val h = Harness(engine = FakeEngine(unavailableReason = "Dictation needs Android 13 or later."))
        h.controller.toggle("text")

        assertEquals(0, h.engine.startCount)
        assertEquals(DictationPhase.IDLE, h.controller.phase)
        assertEquals("Dictation needs Android 13 or later.", h.controller.errorMessage)
    }

    @Test
    fun blankTranscriptChangesNothing() {
        val h = Harness()
        h.dictate("Existing.", "   ")

        assertTrue(h.correctCalls.isEmpty())
        assertTrue(h.delivered.isEmpty())
        assertEquals(DictationPhase.IDLE, h.controller.phase)
    }

    @Test
    fun engineErrorReturnsToIdleWithTheMessage() {
        val h = Harness()
        h.controller.toggle("")
        h.engine.listener!!.onError("Speech recognition is busy.")

        assertEquals(DictationPhase.IDLE, h.controller.phase)
        assertEquals("Speech recognition is busy.", h.controller.errorMessage)
        assertNotNull(h.engine.listener)
    }

    @Test
    fun engineEndingOnItsOwnSalvagesLocallyWithoutACloudCall() {
        val h = Harness()
        h.controller.toggle("Start.")
        h.engine.listener!!.onFinished("heard before it dropped")

        assertTrue("the salvage path makes no cloud call", h.correctCalls.isEmpty())
        assertEquals(listOf("Start. heard before it dropped"), h.delivered)
        assertEquals(DictationPhase.IDLE, h.controller.phase)
    }

    @Test
    fun permissionDeniedExplainsHowToFix() {
        val h = Harness()
        h.controller.reportPermissionDenied()
        assertTrue(h.controller.errorMessage!!.contains("microphone access"))
    }
}
