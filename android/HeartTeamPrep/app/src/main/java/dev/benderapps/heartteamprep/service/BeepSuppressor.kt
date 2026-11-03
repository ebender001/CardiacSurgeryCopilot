package dev.benderapps.heartteamprep.service

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.parse.Parse

/**
 * Silences the speech recognizer's start/stop tones while dictating.
 *
 * Android's `SpeechRecognizer` has no option to turn its earcons off, and
 * dictation restarts it after every pause, so the tones repeat throughout a
 * session. The only lever is the audio streams they play on, so this mutes
 * those streams for the duration of a session and restores them afterward.
 *
 * Streams the person had ALREADY muted are never touched or "restored".
 * Because a mute persists until undone, the set this muted is recorded on
 * disk, and [restoreIfLeftMuted] (called at app start) undoes it if the app
 * was killed mid-dictation -- otherwise their volume could stay muted.
 */
object BeepSuppressor {
    private val streams = intArrayOf(AudioManager.STREAM_NOTIFICATION, AudioManager.STREAM_SYSTEM, AudioManager.STREAM_MUSIC)

    /** Long enough for the closing tone, which can play just after the final result arrives. */
    private const val RESTORE_DELAY_MS = 600L

    private const val PREFS = "heart_team_prep_beep_suppressor"
    private const val KEY_MUTED = "mutedStreams"

    private val handler = Handler(Looper.getMainLooper())
    private val pendingRestore = Runnable { restoreNow() }

    private val context: Context get() = Parse.getApplicationContext()
    private val audio: AudioManager get() = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val prefs get() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Mutes the tone streams. Safe to call repeatedly; cancels a restore still pending from the last session. */
    fun mute() {
        handler.removeCallbacks(pendingRestore)
        val mutedByUs = prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty().toMutableSet()
        for (stream in streams) {
            if (stream.toString() in mutedByUs) continue
            try {
                if (audio.isStreamMute(stream)) continue
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_MUTE, 0)
                mutedByUs += stream.toString()
            } catch (e: SecurityException) {
                // Do Not Disturb can forbid changing some streams; the others still help.
            }
        }
        // Write synchronously: if we die right after, the next launch must know what to undo.
        prefs.edit().putStringSet(KEY_MUTED, mutedByUs).commit()
    }

    /** Restores what [mute] muted, after a short delay so the closing tone stays silent too. */
    fun restoreSoon() {
        handler.removeCallbacks(pendingRestore)
        handler.postDelayed(pendingRestore, RESTORE_DELAY_MS)
    }

    /** Undoes any mute left behind by an earlier run that was killed mid-dictation. Call at app start. */
    fun restoreIfLeftMuted() = restoreNow()

    private fun restoreNow() {
        val mutedByUs = prefs.getStringSet(KEY_MUTED, emptySet()).orEmpty()
        if (mutedByUs.isEmpty()) return
        for (stream in mutedByUs.mapNotNull { it.toIntOrNull() }) {
            try {
                audio.adjustStreamVolume(stream, AudioManager.ADJUST_UNMUTE, 0)
            } catch (e: SecurityException) {
                // Nothing more we can do for this stream.
            }
        }
        prefs.edit().remove(KEY_MUTED).commit()
    }
}
