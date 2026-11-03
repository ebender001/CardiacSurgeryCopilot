package dev.benderapps.heartteamprep.service

import opennlp.tools.namefind.NameFinderME
import opennlp.tools.namefind.TokenNameFinderModel
import opennlp.tools.tokenize.SimpleTokenizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The on-device [NameSpanFinder] replaces `NameFinderME` (which can't be
 * constructed on Android -- see NameSpanFinder). This pins it to the real
 * thing: identical spans on every sentence, for both models.
 */
class NameSpanFinderParityTest {
    private val sentences = listOf(
        "John Smith, a 68-year-old from Chicago, was admitted to Rush University Medical Center on March 5, 2021 with unstable angina.",
        "Mr. Nguyen was seen by Dr. Patel yesterday and his wife, Priya, is the primary caregiver.",
        "Pt Robert Chen, 64M, from Houston, TX, NSTEMI with LVEF 35% and three-vessel disease.",
        "Maria Gonzalez has diabetes and lives in rural Minnesota near Denver.",
        "She immigrated from Brazil in 1998 and works in Springfield; her son David lives in Boston.",
        "Catheterization shows 90% proximal LAD stenosis, 70% OM1, and a 100% occluded RCA with collaterals.",
        "Wang et al. showed benefit; the SYNTAX trial favored CABG over PCI in left main disease.",
        "Referred by the Cleveland Clinic team for a Ross procedure; Whipple was not considered.",
        "The patient, Angela Davis-Moore, was flown from Anchorage to Seattle for transplant evaluation.",
        "Operated on by Dr. O'Brien; discharged to Mercy Hospital rehab in Phoenix, Arizona."
    )

    private fun load(name: String) = File("src/main/assets/phi/$name").inputStream().use { TokenNameFinderModel(it) }

    @Test
    fun matchesNameFinderMeOnBothModels() {
        var totalSpans = 0
        for (modelName in listOf("en-ner-person.bin", "en-ner-location.bin")) {
            val model = load(modelName)
            val reference = NameFinderME(model)
            val custom = NameSpanFinder(model)
            for (sentence in sentences) {
                val tokens = SimpleTokenizer.INSTANCE.tokenize(sentence)
                val expected = reference.find(tokens).map { it.start to it.end }
                val actual = custom.find(tokens).map { it.start to it.end }
                assertEquals("$modelName: $sentence", expected, actual)
                totalSpans += expected.size
                reference.clearAdaptiveData()
                custom.clearAdaptiveData()
            }
        }
        // Guard against a vacuous pass: the corpus must actually contain entities.
        assertTrue("expected the reference finder to find entities, found $totalSpans", totalSpans >= 10)
    }
}
