package dev.benderapps.heartteamprep.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.Date

class ConferenceModelsTest {
    @Test
    fun parsesCaseWithPendingQuestion() {
        val case = ConferenceCase.fromMap(
            mapOf(
                "caseId" to "abc",
                "status" to "collecting_information",
                "originalNarrative" to "A 58-year-old presents with three-vessel disease.",
                "nextQuestion" to mapOf("id" to "q1", "text" to "Is a bloodless protocol in place?", "category" to "patient goals", "reason" to "Shapes the plan.")
            )
        )
        assertEquals(ConferenceCaseStatus.COLLECTING_INFORMATION, case.status)
        assertEquals("q1", case.nextQuestion?.id)
        assertNull(case.report)
        assertTrue(case.conversation.isEmpty())
    }

    @Test
    fun parsesCompletedCaseWithReportAndConversation() {
        val case = ConferenceCase.fromMap(
            mapOf(
                "caseId" to "abc",
                "status" to "completed",
                "report" to mapOf(
                    "diagnosis" to "d", "indication" to "i", "missingInformation" to "m", "operativeStrategy" to "o",
                    "alternatives" to "a", "controversies" to "c", "technicalConsiderations" to "t", "postoperativeConcerns" to "p",
                    "evidenceGuidelines" to listOf(mapOf("topic" to "CABG vs PCI", "searchIntent" to "RCTs", "citation" to null, "verified" to false))
                ),
                "conversation" to listOf(
                    mapOf("questionId" to "q1", "question" to "Q?", "category" to "c", "reason" to "r", "answer" to "A.")
                )
            )
        )
        assertEquals(ConferenceCaseStatus.COMPLETED, case.status)
        assertEquals("CABG vs PCI", case.report?.evidenceGuidelines?.single()?.topic)
        assertNull(case.report?.evidenceGuidelines?.single()?.citation)
        assertEquals("A.", case.conversation.single().answer)
    }

    @Test
    fun missingRequiredKeyFailsDecoding() {
        try {
            ConferenceCase.fromMap(mapOf("status" to "completed"))
            fail("expected BackendDecodingException")
        } catch (e: BackendDecodingException) {
            // expected
        }
    }

    @Test
    fun unknownStatusFailsDecoding() {
        try {
            ConferenceCase.fromMap(mapOf("caseId" to "abc", "status" to "mystery"))
            fail("expected BackendDecodingException")
        } catch (e: BackendDecodingException) {
            // expected
        }
    }

    @Test
    fun parsesSummaryDateFromDateOrIsoMap() {
        val now = Date()
        val fromDate = ConferenceCaseSummary.fromMap(mapOf("caseId" to "1", "title" to "t", "status" to "completed", "createdAt" to now))
        assertEquals(now, fromDate.createdAt)

        val fromIso = ConferenceCaseSummary.fromMap(
            mapOf("caseId" to "1", "title" to "t", "status" to "completed", "createdAt" to mapOf("__type" to "Date", "iso" to "2026-01-02T03:04:05.000Z"))
        )
        assertNotNull(fromIso.createdAt)
    }

    @Test
    fun parsesGradedAndUngradedHeartTeamResponses() {
        fun response(cor: String?, loe: String?) = mapOf(
            "recommendation" to "Proceed with CABG.", "rationale" to "Because.",
            "classOfRecommendation" to cor, "levelOfEvidence" to loe
        )
        val responses = HeartTeamResponses.fromMap(
            mapOf(
                "caseId" to "abc",
                "surgeon" to response("I", "B-R"),
                "nonInterventionalCardiologist" to response(null, null),
                "interventionalCardiologist" to response("III-Harm", "C-EO")
            )
        )
        assertEquals(ClassOfRecommendation.I, responses[HeartTeamRole.SURGEON].classOfRecommendation)
        assertEquals(LevelOfEvidence.B_RANDOMIZED, responses[HeartTeamRole.SURGEON].levelOfEvidence)
        assertNull(responses[HeartTeamRole.NON_INTERVENTIONAL_CARDIOLOGIST].classOfRecommendation)
        assertEquals("Class III: Harm", responses[HeartTeamRole.INTERVENTIONAL_CARDIOLOGIST].classOfRecommendation?.badgeText)
    }

    @Test
    fun parsesArticleWithOptionalFields() {
        val article = PubMedArticle.fromMap(
            mapOf(
                "pmid" to "123", "title" to "T", "authors" to listOf("A B", "C D"), "journal" to "JACC", "year" to null,
                "abstractSections" to listOf(mapOf("label" to null, "text" to "Body")), "url" to "https://pubmed.ncbi.nlm.nih.gov/123/"
            )
        )
        assertEquals("JACC", article.journalAndYear)
        assertNull(article.abstractSections.single().label)
    }
}
