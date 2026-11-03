package dev.benderapps.heartteamprep.service

import com.parse.ParseCloud
import com.parse.ParseException
import dev.benderapps.heartteamprep.model.BackendDecodingException
import dev.benderapps.heartteamprep.model.ConferenceCase
import dev.benderapps.heartteamprep.model.ConferenceCaseSummary
import dev.benderapps.heartteamprep.model.HeartTeamResponses
import dev.benderapps.heartteamprep.model.HeartTeamRole
import dev.benderapps.heartteamprep.model.HeartTeamRoleEvidence
import dev.benderapps.heartteamprep.model.PubMedArticle
import dev.benderapps.heartteamprep.model.asMap
import dev.benderapps.heartteamprep.model.list
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * User-facing errors surfaced by [BackendService]. Raw Parse/server error
 * text is never shown to the trainee -- only these concise messages.
 */
sealed class BackendError(val userMessage: String) : Exception(userMessage) {
    data class Validation(val serverMessage: String) : BackendError(serverMessage)
    data object NotFound : BackendError("This item is no longer available.")
    data class InvalidState(val serverMessage: String) : BackendError(serverMessage)

    /**
     * The caller's Parse session is missing/expired/revoked -- every `csc*`
     * function except dictation correction requires one. [BackendService]
     * also broadcasts [BackendService.sessionExpired] when this is thrown,
     * which lets AuthenticationViewModel force a clean sign-out instead of
     * the app retrying calls a dead session can never pass.
     */
    data object SessionExpired : BackendError("Your session expired. Please sign in again.")
    data object Server : BackendError("Something went wrong. Please try again.")
    data object Network :
        BackendError("Heart Team Prep couldn't reach the server. Check your connection and try again.")
    data object Decoding :
        BackendError("Heart Team Prep couldn't read the server's response. Please try again.")
}

/**
 * The single interface between the app and the Back4App Cloud Functions,
 * mirroring the iOS client's `BackendService.swift`. View models never call
 * Parse Cloud Functions directly, and no AI/clinical logic lives on the
 * client -- all of that is the backend's responsibility.
 *
 * Flow: Screen -> ViewModel -> BackendService -> Back4App Cloud Function.
 */
object BackendService {
    private val _sessionExpired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits whenever a call fails because the local session is no longer valid server-side. */
    val sessionExpired: SharedFlow<Unit> = _sessionExpired.asSharedFlow()

    /**
     * Stores the narrative, extracts structured case information, and
     * either asks one follow-up question or determines the case is already
     * `ready_to_finalize`.
     */
    suspend fun createConferenceCase(narrative: String): ConferenceCase =
        run("cscCreateConferenceCase", mapOf("narrative" to narrative)) { ConferenceCase.fromMap(it.asMap()) }

    /** Submits an answer to the case's current follow-up question; the backend decides what happens next. */
    suspend fun answerConferenceQuestion(caseId: String, questionId: String, answer: String): ConferenceCase =
        run(
            "cscAnswerConferenceQuestion",
            mapOf("caseId" to caseId, "questionId" to questionId, "answer" to answer)
        ) { ConferenceCase.fromMap(it.asMap()) }

    /**
     * Stops the follow-up loop early, moving the case to `ready_to_finalize`
     * and recording the pending question as explicitly declined. The caller
     * must warn the trainee first -- see the interview screen's confirmation.
     */
    suspend fun skipRemainingConferenceQuestions(caseId: String): ConferenceCase =
        run("cscSkipRemainingConferenceQuestions", mapOf("caseId" to caseId)) { ConferenceCase.fromMap(it.asMap()) }

    /** Generates and persists the nine-section report, moving the case to `completed`. */
    suspend fun finalizeConferenceCase(caseId: String): ConferenceCase =
        run("cscFinalizeConferenceCase", mapOf("caseId" to caseId)) { ConferenceCase.fromMap(it.asMap()) }

    /** The full current state of one case -- used to resume an in-progress case from Recent Cases. */
    suspend fun getConferenceCase(caseId: String): ConferenceCase =
        run("cscGetConferenceCase", mapOf("caseId" to caseId)) { ConferenceCase.fromMap(it.asMap()) }

    /** Live PubMed lookup for one evidence/guideline topic from the report. */
    suspend fun findConferenceReferences(topic: String, searchIntent: String, caseId: String): List<PubMedArticle> =
        run(
            "cscFindConferenceReferences",
            mapOf("topic" to topic, "searchIntent" to searchIntent, "caseId" to caseId)
        ) { result -> result.asMap().list("results").map { PubMedArticle.fromMap(it.asMap()) } }

    /** Every Heart Team case the signed-in trainee owns, most recent first. */
    suspend fun listConferenceCases(): List<ConferenceCaseSummary> =
        run("cscListConferenceCases", emptyMap()) { result ->
            result.asMap().list("cases").map { ConferenceCaseSummary.fromMap(it.asMap()) }
        }

    /** The three heart-team-member responses, generated and cached server-side on first call. */
    suspend fun getConferenceHeartTeamResponses(caseId: String): HeartTeamResponses =
        run("cscGetConferenceHeartTeamResponses", mapOf("caseId" to caseId)) { HeartTeamResponses.fromMap(it.asMap()) }

    /** One role's pro/con PubMed evidence; requires the case's heart-team responses to exist already. */
    suspend fun getHeartTeamRoleEvidence(caseId: String, role: HeartTeamRole): HeartTeamRoleEvidence =
        run("cscGetHeartTeamRoleEvidence", mapOf("caseId" to caseId, "role" to role.raw)) {
            HeartTeamRoleEvidence.fromMap(it.asMap())
        }

    private suspend fun <T> run(function: String, params: Map<String, Any>, decode: (Any?) -> T): T {
        val raw = try {
            ParseCloud.callFunctionInBackground<Any>(function, params).await()
        } catch (e: ParseException) {
            val mapped = map(e)
            if (mapped is BackendError.SessionExpired) _sessionExpired.tryEmit(Unit)
            throw mapped
        } catch (e: Exception) {
            throw BackendError.Network
        }
        return try {
            decode(raw)
        } catch (e: BackendDecodingException) {
            throw BackendError.Decoding
        }
    }

    private fun map(error: ParseException): BackendError = when (error.code) {
        ParseException.VALIDATION_ERROR -> BackendError.Validation(error.message ?: "")
        ParseException.OBJECT_NOT_FOUND -> BackendError.NotFound
        ParseException.OPERATION_FORBIDDEN -> BackendError.InvalidState(error.message ?: "")
        ParseException.INVALID_SESSION_TOKEN -> BackendError.SessionExpired
        ParseException.CONNECTION_FAILED, ParseException.TIMEOUT -> BackendError.Network
        else -> BackendError.Server
    }
}
