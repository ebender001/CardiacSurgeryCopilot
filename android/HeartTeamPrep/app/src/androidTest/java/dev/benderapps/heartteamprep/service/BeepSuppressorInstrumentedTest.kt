package dev.benderapps.heartteamprep.service

import android.content.Context
import android.media.AudioManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BeepSuppressorInstrumentedTest {
    private val audio: AudioManager
        get() = InstrumentationRegistry.getInstrumentation().targetContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    @After
    fun cleanUp() = BeepSuppressor.restoreIfLeftMuted()

    @Test
    fun muteSilencesTheMusicStreamAndRestoreBringsItBack() {
        BeepSuppressor.restoreIfLeftMuted()
        val wasMuted = audio.isStreamMute(AudioManager.STREAM_MUSIC)

        BeepSuppressor.mute()
        assertTrue("music stream should be muted during dictation", audio.isStreamMute(AudioManager.STREAM_MUSIC))

        BeepSuppressor.restoreIfLeftMuted()
        assertTrue("restoring should return the stream to how it was", audio.isStreamMute(AudioManager.STREAM_MUSIC) == wasMuted)
    }

    @Test
    fun aStreamTheUserHadAlreadyMutedIsLeftMutedAfterRestore() {
        BeepSuppressor.restoreIfLeftMuted()
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_MUTE, 0)
        try {
            BeepSuppressor.mute()
            BeepSuppressor.restoreIfLeftMuted()
            assertTrue("we must not unmute what we didn't mute", audio.isStreamMute(AudioManager.STREAM_MUSIC))
        } finally {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
        }
        assertFalse(audio.isStreamMute(AudioManager.STREAM_MUSIC))
    }
}
