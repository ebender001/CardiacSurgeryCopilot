package dev.benderapps.heartteamprep.model

import java.util.Date

/**
 * Client-side representations of the backend's Heart Team case payloads,
 * mirroring the iOS client's `Models/` folder. The Parse Android SDK hands
 * Cloud Function results back as plain `Map`/`List` trees, so each type
 * has a `fromMap` that reads only the keys the UI needs and ignores the
 * rest (the same effect as Swift's `Decodable` skipping unknown keys).
 * A missing required key throws [BackendDecodingException], which
 * `BackendService` maps to its "couldn't read the response" error.
 */
class BackendDecodingException(message: String) : Exception(message)

/** Mirrors the backend's `collecting_information` / `ready_to_finalize` / `completed` state machine. */
enum class ConferenceCaseStatus(val raw: String) {
    COLLECTING_INFORMATION("collecting_information"),
    READY_TO_FINALIZE("ready_to_finalize"),
    COMPLETED("completed");

    companion object {
        fun fromRaw(raw: String): ConferenceCaseStatus =
            entries.firstOrNull { it.raw == raw } ?: throw BackendDecodingException("Unknown status: $raw")
    }
}

data class ConferenceQuestion(val id: String, val text: String, val category: String, val reason: String) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceQuestion(
            id = map.string("id"),
            text = map.string("text"),
            category = map.string("category"),
            reason = map.string("reason")
        )
    }
}

/** One already-answered follow-up question, as returned inline on `cscGetConferenceCase`. */
data class ConferenceConversationEntry(
    val questionId: String,
    val question: String,
    val category: String,
    val reason: String,
    val answer: String
) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceConversationEntry(
            questionId = map.string("questionId"),
            question = map.string("question"),
            category = map.string("category"),
            reason = map.string("reason"),
            answer = map.string("answer")
        )
    }
}

/**
 * A literature/guideline topic identified by the backend for the report's
 * evidence section -- a topic to search, not a verified citation.
 */
data class ConferenceReferenceTopic(
    val topic: String,
    val searchIntent: String,
    val citation: String?,
    val verified: Boolean
) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceReferenceTopic(
            topic = map.string("topic"),
            searchIntent = map.optString("searchIntent") ?: "",
            citation = map.optString("citation"),
            verified = map["verified"] as? Boolean ?: false
        )
    }
}

/** The nine-section preoperative case conference report. */
data class ConferenceReport(
    val diagnosis: String,
    val indication: String,
    val missingInformation: String,
    val operativeStrategy: String,
    val alternatives: String,
    val controversies: String,
    val technicalConsiderations: String,
    val postoperativeConcerns: String,
    val evidenceGuidelines: List<ConferenceReferenceTopic>
) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceReport(
            diagnosis = map.string("diagnosis"),
            indication = map.string("indication"),
            missingInformation = map.string("missingInformation"),
            operativeStrategy = map.string("operativeStrategy"),
            alternatives = map.string("alternatives"),
            controversies = map.string("controversies"),
            technicalConsiderations = map.string("technicalConsiderations"),
            postoperativeConcerns = map.string("postoperativeConcerns"),
            evidenceGuidelines = map.list("evidenceGuidelines").map { ConferenceReferenceTopic.fromMap(it.asMap()) }
        )
    }
}

/**
 * The single type returned by every case-scoped call (create / answer /
 * skip / finalize / get); each returns a subset of these fields, so
 * everything beyond `id` and `status` is optional.
 */
data class ConferenceCase(
    val id: String,
    val status: ConferenceCaseStatus,
    val originalNarrative: String? = null,
    val nextQuestion: ConferenceQuestion? = null,
    val report: ConferenceReport? = null,
    val conversation: List<ConferenceConversationEntry> = emptyList()
) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceCase(
            id = map.string("caseId"),
            status = ConferenceCaseStatus.fromRaw(map.string("status")),
            originalNarrative = map.optString("originalNarrative"),
            nextQuestion = (map["nextQuestion"] as? Map<*, *>)?.let { ConferenceQuestion.fromMap(it) },
            report = (map["report"] as? Map<*, *>)?.let { ConferenceReport.fromMap(it) },
            conversation = map.list("conversation").map { ConferenceConversationEntry.fromMap(it.asMap()) }
        )
    }
}

/** One row in the Recent Cases list, from `cscListConferenceCases`. */
data class ConferenceCaseSummary(
    val id: String,
    val title: String,
    val createdAt: Date?,
    val status: ConferenceCaseStatus
) {
    companion object {
        fun fromMap(map: Map<*, *>) = ConferenceCaseSummary(
            id = map.string("caseId"),
            title = map.string("title"),
            createdAt = map["createdAt"].asDate(),
            status = ConferenceCaseStatus.fromRaw(map.string("status"))
        )
    }
}

data class PubMedAbstractSection(val label: String?, val text: String)

data class PubMedArticle(
    val pmid: String,
    val title: String,
    val authors: List<String>,
    val journal: String?,
    val year: String?,
    val abstractSections: List<PubMedAbstractSection>,
    val url: String
) {
    /** "Journal • Year", or whichever of the two exists. */
    val journalAndYear: String
        get() = listOfNotNull(journal, year).joinToString(" • ")

    companion object {
        fun fromMap(map: Map<*, *>) = PubMedArticle(
            pmid = map.string("pmid"),
            title = map.string("title"),
            authors = map.list("authors").mapNotNull { it as? String },
            journal = map.optString("journal"),
            year = map.optString("year"),
            abstractSections = map.list("abstractSections").map {
                val section = it.asMap()
                PubMedAbstractSection(label = section.optString("label"), text = section.string("text"))
            },
            url = map.optString("url") ?: ""
        )
    }
}

data class PubMedEvidenceSet(val query: String, val results: List<PubMedArticle>) {
    companion object {
        fun fromMap(map: Map<*, *>) = PubMedEvidenceSet(
            query = map.optString("query") ?: "",
            results = map.list("results").map { PubMedArticle.fromMap(it.asMap()) }
        )
    }
}

data class HeartTeamRoleEvidence(val caseId: String, val role: HeartTeamRole, val pro: PubMedEvidenceSet, val con: PubMedEvidenceSet) {
    companion object {
        fun fromMap(map: Map<*, *>) = HeartTeamRoleEvidence(
            caseId = map.string("caseId"),
            role = HeartTeamRole.fromRaw(map.string("role")),
            pro = PubMedEvidenceSet.fromMap(map.asMapKey("pro")),
            con = PubMedEvidenceSet.fromMap(map.asMapKey("con"))
        )
    }
}

enum class HeartTeamRole(val raw: String, val displayName: String, val shortName: String) {
    SURGEON("surgeon", "Cardiac Surgeon", "Surgeon"),
    NON_INTERVENTIONAL_CARDIOLOGIST("nonInterventionalCardiologist", "Non-Interventional Cardiologist", "Non-Interventional"),
    INTERVENTIONAL_CARDIOLOGIST("interventionalCardiologist", "Interventional Cardiologist", "Interventional");

    companion object {
        fun fromRaw(raw: String): HeartTeamRole =
            entries.firstOrNull { it.raw == raw } ?: throw BackendDecodingException("Unknown role: $raw")
    }
}

/**
 * ACC/AHA-style Class of Recommendation. The backend already normalizes
 * anything it doesn't recognize to `null`, so an unknown raw value here
 * would be a real client/server disagreement and fails decoding.
 */
enum class ClassOfRecommendation(val raw: String, val badgeText: String) {
    I("I", "Class I"),
    IIA("IIa", "Class IIa"),
    IIB("IIb", "Class IIb"),
    III_NO_BENEFIT("III-NoBenefit", "Class III: No Benefit"),
    III_HARM("III-Harm", "Class III: Harm");

    companion object {
        fun fromRaw(raw: String): ClassOfRecommendation =
            entries.firstOrNull { it.raw == raw } ?: throw BackendDecodingException("Unknown COR: $raw")
    }
}

/** ACC/AHA-style Level of Evidence, graded independently of [ClassOfRecommendation]. */
enum class LevelOfEvidence(val raw: String) {
    A("A"),
    B_RANDOMIZED("B-R"),
    B_NONRANDOMIZED("B-NR"),
    C_LIMITED_DATA("C-LD"),
    C_EXPERT_OPINION("C-EO");

    val badgeText: String get() = "LOE $raw"

    companion object {
        fun fromRaw(raw: String): LevelOfEvidence =
            entries.firstOrNull { it.raw == raw } ?: throw BackendDecodingException("Unknown LOE: $raw")
    }
}

data class HeartTeamResponse(
    val recommendation: String,
    val rationale: String,
    /** `null` when the AI omitted grading -- never a placeholder; the badge is simply not shown. */
    val classOfRecommendation: ClassOfRecommendation?,
    val levelOfEvidence: LevelOfEvidence?
) {
    companion object {
        fun fromMap(map: Map<*, *>) = HeartTeamResponse(
            recommendation = map.string("recommendation"),
            rationale = map.string("rationale"),
            classOfRecommendation = map.optString("classOfRecommendation")?.let { ClassOfRecommendation.fromRaw(it) },
            levelOfEvidence = map.optString("levelOfEvidence")?.let { LevelOfEvidence.fromRaw(it) }
        )
    }
}

data class HeartTeamResponses(
    val caseId: String,
    val surgeon: HeartTeamResponse,
    val nonInterventionalCardiologist: HeartTeamResponse,
    val interventionalCardiologist: HeartTeamResponse
) {
    operator fun get(role: HeartTeamRole): HeartTeamResponse = when (role) {
        HeartTeamRole.SURGEON -> surgeon
        HeartTeamRole.NON_INTERVENTIONAL_CARDIOLOGIST -> nonInterventionalCardiologist
        HeartTeamRole.INTERVENTIONAL_CARDIOLOGIST -> interventionalCardiologist
    }

    companion object {
        fun fromMap(map: Map<*, *>) = HeartTeamResponses(
            caseId = map.string("caseId"),
            surgeon = HeartTeamResponse.fromMap(map.asMapKey("surgeon")),
            nonInterventionalCardiologist = HeartTeamResponse.fromMap(map.asMapKey("nonInterventionalCardiologist")),
            interventionalCardiologist = HeartTeamResponse.fromMap(map.asMapKey("interventionalCardiologist"))
        )
    }
}

/** Result of `cscCorrectDictation` -- the segment with medical terms corrected, plus what changed. */
data class CorrectedDictationSegment(val correctedSegment: String, val changes: List<Change>) {
    data class Change(val original: String, val corrected: String)

    companion object {
        fun fromMap(map: Map<*, *>) = CorrectedDictationSegment(
            correctedSegment = map.string("correctedSegment"),
            changes = map.list("changes").map {
                val change = it.asMap()
                Change(original = change.string("original"), corrected = change.string("corrected"))
            }
        )
    }
}

// MARK: - Map helpers

internal fun Any?.asMap(): Map<*, *> = this as? Map<*, *> ?: throw BackendDecodingException("Expected an object")

internal fun Map<*, *>.asMapKey(key: String): Map<*, *> = this[key].asMap()

internal fun Map<*, *>.string(key: String): String =
    this[key] as? String ?: throw BackendDecodingException("Missing string: $key")

/** A string value, or `null` when the key is absent or JSON `null`. */
internal fun Map<*, *>.optString(key: String): String? = this[key] as? String

internal fun Map<*, *>.list(key: String): List<*> = this[key] as? List<*> ?: emptyList<Any?>()

/** Parse hands dates back as `java.util.Date`; tolerate the raw `{__type, iso}` and ISO-string shapes too. */
internal fun Any?.asDate(): Date? = when (this) {
    is Date -> this
    is Map<*, *> -> (this["iso"] as? String)?.let { parseIso(it) }
    is String -> parseIso(this)
    else -> null
}

private fun parseIso(value: String): Date? = try {
    Date.from(java.time.Instant.parse(value))
} catch (e: Exception) {
    null
}
