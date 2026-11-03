package dev.benderapps.heartteamprep.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.benderapps.heartteamprep.model.ConferenceConversationEntry
import dev.benderapps.heartteamprep.model.ConferenceReport
import dev.benderapps.heartteamprep.model.HeartTeamResponses
import dev.benderapps.heartteamprep.model.HeartTeamRole
import dev.benderapps.heartteamprep.model.HeartTeamRoleEvidence
import dev.benderapps.heartteamprep.model.PubMedArticle
import dev.benderapps.heartteamprep.service.BackendError
import dev.benderapps.heartteamprep.service.BackendService
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

private fun userMessage(e: Exception, fallback: String) = (e as? BackendError)?.userMessage ?: fallback

/**
 * Loads (and holds, since the backend already caches per-case) the three
 * heart-team responses, and tracks which role is selected. Mirrors the iOS
 * `HeartTeamResponsesViewModel`.
 */
class HeartTeamResponsesViewModel(val caseId: String) : ViewModel() {
    var responses: HeartTeamResponses? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set
    var selectedRole: HeartTeamRole by mutableStateOf(HeartTeamRole.SURGEON)

    /**
     * Every already-answered follow-up question, oldest first -- empty means
     * none were needed, not that it hasn't loaded yet.
     */
    var conversation: List<ConferenceConversationEntry> by mutableStateOf(emptyList())
        private set

    init {
        load()
    }

    fun load() {
        if (responses != null || isLoading) return
        isLoading = true
        viewModelScope.launch {
            // The follow-up history is supplementary and read-only -- its
            // failure must never block the main responses display.
            val conversationFetch = async {
                try {
                    BackendService.getConferenceCase(caseId).conversation
                } catch (e: Exception) {
                    emptyList()
                }
            }
            try {
                responses = BackendService.getConferenceHeartTeamResponses(caseId)
                conversation = conversationFetch.await()
                errorMessage = null
            } catch (e: Exception) {
                errorMessage = userMessage(e, "Couldn't load heart team responses. Please try again.")
            } finally {
                isLoading = false
            }
        }
    }
}

/**
 * Reached either from an already-`completed` Recent Cases row or from the
 * heart-team screen's "View Full Report" on a case that hasn't been
 * finalized yet -- [load] handles both by fetching the case and finalizing
 * it itself if no report exists, so the screen never has to know which path
 * got it here.
 */
class ConferenceReportViewModel(val caseId: String) : ViewModel() {
    var report: ConferenceReport? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)

    init {
        load()
    }

    fun load() {
        if (report != null || isLoading) return
        errorMessage = null
        isLoading = true
        viewModelScope.launch {
            try {
                val existing = BackendService.getConferenceCase(caseId)
                report = existing.report ?: BackendService.finalizeConferenceCase(caseId).report
            } catch (e: Exception) {
                errorMessage = userMessage(e, "Couldn't load the report. Please try again.")
            } finally {
                isLoading = false
            }
        }
    }
}

/** Loads one role's pro/con PubMed evidence (cached server-side). */
class HeartTeamEvidenceViewModel(val caseId: String, val role: HeartTeamRole) : ViewModel() {
    var evidence: HeartTeamRoleEvidence? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)
        private set

    init {
        load()
    }

    fun load() {
        if (evidence != null || isLoading) return
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                evidence = BackendService.getHeartTeamRoleEvidence(caseId, role)
            } catch (e: Exception) {
                errorMessage = userMessage(e, "Couldn't load evidence. Please try again.")
            } finally {
                isLoading = false
            }
        }
    }
}

/** An on-demand PubMed search for one evidence/guideline topic from the report. */
class ConferenceReferenceLookupViewModel(
    val caseId: String,
    val topic: String,
    val searchIntent: String
) : ViewModel() {
    var results: List<PubMedArticle>? by mutableStateOf(null)
        private set
    var isLoading: Boolean by mutableStateOf(false)
        private set
    var errorMessage: String? by mutableStateOf(null)

    init {
        search()
    }

    fun search() {
        if (results != null || isLoading) return
        errorMessage = null
        isLoading = true
        viewModelScope.launch {
            try {
                results = BackendService.findConferenceReferences(topic, searchIntent, caseId)
            } catch (e: Exception) {
                errorMessage = userMessage(e, "Couldn't search PubMed. Please try again.")
            } finally {
                isLoading = false
            }
        }
    }
}
