package dev.benderapps.heartteamprep.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.io.File

/** Runs the real bundled models and word lists (read straight from the assets folder). */
class PHIFilterServiceTest {
    companion object {
        private lateinit var filter: PHIFilter

        @BeforeClass
        @JvmStatic
        fun loadFilter() {
            val assets = File("src/main/assets/phi")
            val words = HashSet<String>()
            listOf("MedicalDictionary.txt", "CVTMedicalDictionary.txt").forEach { name ->
                File(assets, name).useLines { lines -> lines.forEach { l -> l.trim().takeIf { it.isNotEmpty() }?.let { words += it.lowercase() } } }
            }
            filter = PHIFilter(File(assets, "en-ner-person.bin").inputStream(), File(assets, "en-ner-location.bin").inputStream(), words)
        }
    }

    private fun categories(text: String) = filter.redact(text).findings.map { it.category }

    @Test
    fun cleanClinicalTextIsUntouched() {
        val clean = listOf(
            "A 64-year-old man presents with an NSTEMI and three-vessel disease. LVEF is 35%.",
            "Catheterization shows 90% proximal LAD stenosis, 70% OM1, and a 100% occluded RCA with collaterals.",
            "He underwent CABG x3 with LIMA to LAD and SVG to OM1 and PDA; cross-clamp time was 92 minutes.",
            "Echo shows severe aortic stenosis with a mean gradient of 48 mmHg and a valve area of 0.7 cm2.",
            "Plan is a Whipple-style approach is not applicable; consider a Bentall procedure for the root aneurysm.",
            "The patient has Lyme disease and Crohn's disease, and takes aspirin, metoprolol, and atorvastatin.",
            "He was transferred from an outside hospital to the clinic for evaluation."
        )
        clean.forEach { text ->
            val result = filter.redact(text)
            assertEquals("unexpected redaction in: $text -> ${result.redactedText}", text, result.redactedText)
        }
    }

    @Test
    fun redactsPersonNames() {
        val cases = listOf(
            "Patient John Smith presents with chest pain.",
            "The case was referred by Dr. Alvarez for a second opinion.",
            "Mrs. Johnson is a 72-year-old woman with mitral regurgitation.",
            "Maria Gonzalez has three-vessel disease and diabetes."
        )
        cases.forEach { text ->
            val result = filter.redact(text)
            assertTrue("no NAME found in: $text -> ${result.redactedText}", PHIFinding.Category.NAME in result.findings.map { it.category })
            assertFalse(result.redactedText.contains("Smith") || result.redactedText.contains("Alvarez") ||
                result.redactedText.contains("Johnson") || result.redactedText.contains("Gonzalez"))
        }
    }

    @Test
    fun redactsApostropheNamesIntroducedNamesAndStateCodes() {
        assertEquals("Operated on Tuesday by [name removed]; discharged.", filter.redact("Operated on Tuesday by Dr. O'Brien; discharged.").redactedText)
        assertEquals("The patient, named [name removed] has CAD.", filter.redact("The patient, named john smith has CAD.").redactedText)
        assertEquals("He is from [location removed] and has CAD.", filter.redact("He is from Houston, TX and has CAD.").redactedText)
        // A clinical abbreviation that merely looks like a state code stays put.
        assertEquals("Prior MI and PA pressure of 40.", filter.redact("Prior MI and PA pressure of 40.").redactedText)
    }

    @Test
    fun redactsLocations() {
        val cases = listOf(
            "He lives in Chicago and was flown in from Denver." to listOf("Chicago", "Denver"),
            "The patient immigrated from Brazil in his twenties." to listOf("Brazil"),
            "She resides at 742 Evergreen Terrace Road." to listOf("742 Evergreen Terrace Road")
        )
        cases.forEach { (text, removed) ->
            val result = filter.redact(text)
            assertTrue("no LOCATION in: $text -> ${result.redactedText}", PHIFinding.Category.LOCATION in result.findings.map { it.category })
            removed.forEach { assertFalse("'$it' survived: ${result.redactedText}", result.redactedText.contains(it)) }
        }
    }

    @Test
    fun redactsInstitutions() {
        val result = filter.redact("Transferred from Massachusetts General Hospital last week.")
        assertEquals("Transferred from [institution removed] last week.", result.redactedText)
        assertEquals(PHIFinding.Category.INSTITUTION, result.findings.single().category)
    }

    @Test
    fun redactsExplicitDates() {
        val cases = mapOf(
            "Surgery on March 5, 2021 went well." to "Surgery on [date removed] went well.",
            "Seen on 5 March 2021 in clinic." to "Seen on [date removed] in clinic.",
            "Echo 3/5/2021 showed LVEF 30%." to "Echo [date removed] showed LVEF 30%.",
            "Admitted in March 2021 with chest pain." to "Admitted in [date removed] with chest pain.",
            "Diagnosed with diabetes in 2015." to "Diagnosed with diabetes [date removed]."
        )
        cases.forEach { (input, expected) ->
            val result = filter.redact(input)
            assertEquals(expected, result.redactedText)
            assertEquals(PHIFinding.Category.DATE, result.findings.single().category)
        }
    }

    @Test
    fun doesNotTreatClinicalNumbersAsDates() {
        val text = "LVEF 35%, 90% proximal LAD stenosis, gradient 40 mmHg, 2 vessels, 2000 mL blood loss."
        assertTrue(categories(text).isEmpty())
    }

    @Test
    fun findsEverythingInOneNarrative() {
        val text = "John Smith, a 68-year-old from Chicago, was admitted to Rush University Medical Center on March 5, 2021 with unstable angina."
        val result = filter.redact(text)
        val found = result.findings.map { it.category }.toSet()
        assertEquals(
            setOf(PHIFinding.Category.NAME, PHIFinding.Category.LOCATION, PHIFinding.Category.INSTITUTION, PHIFinding.Category.DATE),
            found
        )
        listOf("Smith", "Chicago", "Rush", "March").forEach { assertFalse("'$it' survived: ${result.redactedText}", result.redactedText.contains(it)) }
        assertTrue(result.redactedText.contains("unstable angina"))
    }

    @Test
    fun noticeNamesCategoriesWithoutRepeatingRemovedText() {
        val result = filter.redact("At Mount Sinai Hospital on March 5, 2021 he had a CABG.")
        val notice = result.noticeMessage!!
        assertTrue(notice.contains("a hospital or institution name and a specific date"))
        assertFalse(notice.contains("Mount Sinai"))
        assertFalse(notice.contains("March"))
        assertNull(filter.redact("Severe aortic stenosis.").noticeMessage)
    }
}
