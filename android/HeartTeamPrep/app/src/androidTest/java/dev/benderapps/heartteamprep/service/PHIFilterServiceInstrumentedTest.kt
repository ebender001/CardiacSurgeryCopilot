package dev.benderapps.heartteamprep.service

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs the real [PHIFilterService] -- bundled assets, OpenNLP, and Android's
 * runtime -- on a device or emulator. The JVM tests can't catch Android-only
 * failures (e.g. OpenNLP's default feature generators needing an XML parser
 * feature Android lacks), which would otherwise only show up as the app
 * refusing to send anything.
 */
@RunWith(AndroidJUnit4::class)
class PHIFilterServiceInstrumentedTest {
    @Test
    fun loadsAndRedactsEveryCategoryOnDevice() {
        val text = "John Smith, a 68-year-old from Chicago, was admitted to Rush University Medical Center on March 5, 2021 with unstable angina."
        val result = PHIFilterService.redact(text)

        assertEquals(
            setOf(PHIFinding.Category.NAME, PHIFinding.Category.LOCATION, PHIFinding.Category.INSTITUTION, PHIFinding.Category.DATE),
            result.findings.map { it.category }.toSet()
        )
        listOf("Smith", "Chicago", "Rush", "March").forEach {
            assertFalse("'$it' survived: ${result.redactedText}", result.redactedText.contains(it))
        }
        assertTrue(result.redactedText.contains("unstable angina"))
    }

    @Test
    fun leavesCleanClinicalTextAlone() {
        val text = "Catheterization shows 90% proximal LAD stenosis and a 100% occluded RCA; LVEF is 35%."
        assertEquals(text, PHIFilterService.redact(text).redactedText)
    }
}
