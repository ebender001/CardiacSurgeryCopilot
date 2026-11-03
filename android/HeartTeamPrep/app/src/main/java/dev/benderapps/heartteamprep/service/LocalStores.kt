package dev.benderapps.heartteamprep.service

import android.content.Context
import android.content.SharedPreferences
import com.parse.Parse

private fun prefs(): SharedPreferences =
    Parse.getApplicationContext().getSharedPreferences("heart_team_prep", Context.MODE_PRIVATE)

/**
 * Persists whether the trainee has acknowledged that case data is sent to
 * OpenAI (see AIDataConsentScreen). Device-local, mirroring the iOS
 * client's `AIDataConsentStore`.
 */
class AIDataConsentStore {
    private val key = "hasConsentedToAIDataSharing"

    var hasConsented: Boolean
        get() = prefs().getBoolean(key, false)
        set(value) = prefs().edit().putBoolean(key, value).apply()
}

/**
 * The Recent Cases list is sourced entirely from the backend -- this holds
 * only a small local denylist of case ids the trainee has swiped away, so
 * "removing" an item stays a per-device UI preference rather than deleting
 * the case's backend record. Mirrors the iOS `HiddenCaseIdsStore`.
 */
class HiddenCaseIdsStore(namespace: String) {
    private val key = "hiddenCaseIds.$namespace"

    fun all(): Set<String> = prefs().getStringSet(key, emptySet()) ?: emptySet()

    fun hide(id: String) {
        // getStringSet's result must not be mutated in place -- copy first.
        prefs().edit().putStringSet(key, all() + id).apply()
    }
}
