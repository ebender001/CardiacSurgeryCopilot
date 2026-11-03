package dev.benderapps.heartteamprep.service

import android.util.Log
import com.parse.Parse
import opennlp.tools.namefind.DefaultNameContextGenerator
import opennlp.tools.namefind.TokenNameFinderModel
import opennlp.tools.tokenize.SimpleTokenizer
import opennlp.tools.util.Span
import opennlp.tools.util.featuregen.BigramNameFeatureGenerator
import opennlp.tools.util.featuregen.OutcomePriorFeatureGenerator
import opennlp.tools.util.featuregen.PreviousMapFeatureGenerator
import opennlp.tools.util.featuregen.SentenceFeatureGenerator
import opennlp.tools.util.featuregen.TokenClassFeatureGenerator
import opennlp.tools.util.featuregen.TokenFeatureGenerator
import opennlp.tools.util.featuregen.WindowFeatureGenerator
import java.io.InputStream

/**
 * On-device-only screen for PHI in anything the trainee types, applied
 * before that text is allowed to reach ANY network call (the case-creation
 * and answer-submission Cloud Functions both forward text to OpenAI).
 * Nothing here makes a network call; it only rewrites a local string.
 *
 * Scope matches the iOS client's `PHIFilterService`: patient/staff names,
 * hospital/institution names, specific dates, and place names. This is NOT
 * a full HIPAA Safe Harbor de-identification (that also covers ages >89,
 * MRNs, phone numbers, etc.) -- it is a safety net that catches obvious
 * slips, not a substitute for the trainee's own judgment.
 *
 * Detection differs from iOS because Android has no `NLTagger`:
 *  - names and places come from bundled Apache OpenNLP models (see
 *    assets/phi/README.md), which are statistical and trained on news text,
 *    so they miss some names and mislabel some words;
 *  - a medical-word list (shared with iOS) and small allowlists suppress
 *    the most common false positives (eponyms like "Whipple", diseases
 *    named for places like "Lyme");
 *  - institutions, dates, street addresses, and "Dr./Mr./Ms. <Name>" use
 *    patterns.
 * Expect somewhat lower recall than iOS on unusual names.
 */
data class PHIFinding(val category: Category, val originalText: String) {
    enum class Category(val label: String, val placeholder: String) {
        NAME("a patient or staff name", "[name removed]"),
        INSTITUTION("a hospital or institution name", "[institution removed]"),
        DATE("a specific date", "[date removed]"),
        LOCATION("a geographic location", "[location removed]")
    }
}

data class PHIFilterResult(val redactedText: String, val findings: List<PHIFinding>) {
    val hasFindings: Boolean get() = findings.isNotEmpty()

    /** A short summary of what was removed, grouped by category -- never repeats the removed text itself. */
    val noticeMessage: String?
        get() {
            if (findings.isEmpty()) return null
            val categories = findings.map { it.category }.toSet()
            val labels = PHIFinding.Category.entries.filter { it in categories }.map { it.label }
            val joined = when (labels.size) {
                1 -> labels[0]
                2 -> "${labels[0]} and ${labels[1]}"
                else -> labels.dropLast(1).joinToString(", ") + ", and " + labels.last()
            }
            return "We removed what looked like $joined before sending this. " +
                "Please describe cases without patient names, staff or institution names, specific dates, or geographic locations."
        }
}

/**
 * A named-entity finder over an OpenNLP model, built WITHOUT `NameFinderME`.
 *
 * `NameFinderME`'s constructor builds its feature generators by parsing an
 * XML descriptor with a JAXP security feature Android's XML parser rejects
 * (`IllegalStateException ... secure-processing`), so on a device it can't
 * be constructed at all -- while it works fine in JVM unit tests. This
 * reproduces what `NameFinderME.find` does using the same public pieces
 * (the model's beam-search sequence model, its codec, and the context
 * generator), with the feature generators the library's default descriptor
 * (`ner-default-features.xml`) specifies, constructed in code. Those must
 * match what the models were trained with; PHIFilterServiceTest checks this
 * against the real `NameFinderME` on the JVM.
 */
internal class NameSpanFinder(model: TokenNameFinderModel) {
    private val sequenceModel = model.nameFinderSequenceModel
    private val codec = model.sequenceCodec
    private val validator = codec.createSequenceValidator()
    private val contextGenerator = DefaultNameContextGenerator(
        WindowFeatureGenerator(TokenClassFeatureGenerator(true), 2, 2),
        WindowFeatureGenerator(TokenFeatureGenerator(true), 2, 2),
        OutcomePriorFeatureGenerator(),
        PreviousMapFeatureGenerator(),
        BigramNameFeatureGenerator(),
        SentenceFeatureGenerator(true, false)
    )

    fun find(tokens: Array<String>): Array<Span> {
        val best = sequenceModel.bestSequence(tokens, emptyArray<Array<String>>(), contextGenerator, validator)
        val outcomes = best.outcomes
        contextGenerator.updateAdaptiveData(tokens, outcomes.toTypedArray())
        return codec.decode(outcomes)
    }

    fun clearAdaptiveData() = contextGenerator.clearAdaptiveData()
}

/**
 * The detection logic, with its models and word list passed in so tests can
 * build one from files while the app builds one from assets (see
 * [PHIFilterService]).
 */
class PHIFilter(
    personModel: InputStream,
    locationModel: InputStream,
    private val medicalWords: Set<String>
) {
    private val personFinder = NameSpanFinder(personModel.use { TokenNameFinderModel(it) })
    private val locationFinder = NameSpanFinder(locationModel.use { TokenNameFinderModel(it) })

    // NameFinderME keeps per-document state and isn't thread-safe.
    private val lock = Any()

    fun redact(text: String): PHIFilterResult {
        if (text.isEmpty()) return PHIFilterResult(text, emptyList())
        val matches = locatedMatches(text)
        if (matches.isEmpty()) return PHIFilterResult(text, emptyList())

        val findings = mutableListOf<PHIFinding>()
        val redacted = StringBuilder()
        var cursor = 0
        for (match in matches) {
            redacted.append(text, cursor, match.start)
            findings += PHIFinding(match.category, text.substring(match.start, match.end))
            redacted.append(match.category.placeholder)
            cursor = match.end
        }
        redacted.append(text, cursor, text.length)
        return PHIFilterResult(redacted.toString(), findings)
    }

    private data class Match(val start: Int, val end: Int, val category: PHIFinding.Category)

    /** Every non-overlapping match, sorted by position (longest first when two start together). */
    private fun locatedMatches(text: String): List<Match> {
        val all = mutableListOf<Match>()

        institutionPattern.findAll(text).forEach { all += Match(it.range.first, it.range.last + 1, PHIFinding.Category.INSTITUTION) }
        datePatterns.forEach { pattern ->
            pattern.findAll(text).forEach { all += Match(it.range.first, it.range.last + 1, PHIFinding.Category.DATE) }
        }
        addressPattern.findAll(text).forEach { all += Match(it.range.first, it.range.last + 1, PHIFinding.Category.LOCATION) }
        titledNamePattern.findAll(text).forEach { match ->
            val nameRange = match.groups[1]!!.range
            if (!isKnownTerm(text.substring(nameRange.first, nameRange.last + 1), eponymAllowlist)) {
                all += Match(match.range.first, match.range.last + 1, PHIFinding.Category.NAME)
            }
        }
        introducedNamePattern.findAll(text).forEach { match ->
            val group = match.groups[1]!!
            val words = wordPattern.findAll(group.value).toList()
            val kept = words.filterIndexed { i, w -> i == 0 || w.value.lowercase() !in nameTerminators }
                .takeWhile { !isKnownTerm(it.value, eponymAllowlist) }
            if (kept.isNotEmpty()) {
                all += Match(
                    group.range.first + kept.first().range.first,
                    group.range.first + kept.last().range.last + 1,
                    PHIFinding.Category.NAME
                )
            }
        }
        all += modelMatches(text)

        all.sortWith(compareBy<Match> { it.start }.thenByDescending { it.end })
        val result = mutableListOf<Match>()
        var lastEnd = 0
        for (match in all) {
            if (match.start >= lastEnd) {
                result += match
                lastEnd = match.end
            }
        }
        return result
    }

    private fun modelMatches(text: String): List<Match> {
        val spans = SimpleTokenizer.INSTANCE.tokenizePos(text)
        if (spans.isEmpty()) return emptyList()
        val tokens = Array(spans.size) { text.substring(spans[it].start, spans[it].end) }

        val out = mutableListOf<Match>()
        synchronized(lock) {
            for ((finder, category, allowlist) in listOf(
                Triple(personFinder, PHIFinding.Category.NAME, eponymAllowlist),
                Triple(locationFinder, PHIFinding.Category.LOCATION, geographicMedicalTermAllowlist)
            )) {
                finder.find(tokens).forEach { span ->
                    val spanTokens = tokens.slice(span.start until span.end)
                    if (spanTokens.all { isIgnorable(it, allowlist) }) return@forEach
                    var end = spans[span.end - 1].end
                    if (category == PHIFinding.Category.LOCATION) {
                        trailingStateCode.find(text.substring(end))?.let { end += it.range.last + 1 }
                    }
                    out += Match(spans[span.start].start, end, category)
                }
                finder.clearAdaptiveData()
            }
        }
        return out
    }

    /**
     * A token the models should never be allowed to redact on their own:
     * punctuation/numbers, an all-caps acronym (LAD, CABG, OM1 -- not how
     * people type names), or a known medical term.
     */
    private fun isIgnorable(token: String, allowlist: Set<String>): Boolean {
        if (token.none { it.isLetter() }) return true
        if (token.length in 2..6 && token.all { it.isUpperCase() || it.isDigit() }) return true
        return isKnownTerm(token, allowlist)
    }

    private fun isKnownTerm(word: String, allowlist: Set<String>): Boolean {
        val lower = word.lowercase()
        return lower in allowlist || lower in medicalWords
    }

    private companion object {
        /**
         * Medical eponyms a generic name detector can mistake for a
         * patient/staff name (e.g. "Whipple procedure") -- never redacted
         * even when they look like a person's name. Same list as iOS.
         */
        val eponymAllowlist = setOf(
            "whipple", "crohn", "crohn's", "nissen", "zenker", "barrett", "barrett's",
            "courvoisier", "richter", "meckel", "ladd", "kocher", "pringle",
            "billroth", "roux", "heller", "hartmann", "miles", "graham",
            "boerhaave", "mallory", "mallory-weiss", "weiss", "curling", "cushing",
            "virchow", "charcot", "murphy", "mcburney", "rovsing", "cullen",
            "ranson", "sengstaken", "blakemore",
            // Unambiguous cardiac eponyms only. Ones that are also common
            // first names/surnames (Ross, David, Edwards, Cox, Swan, Glenn...)
            // are deliberately left out: missing a patient name is worse than
            // redacting "Ross" in "Ross procedure".
            "bentall", "konno", "norwood", "fontan", "blalock", "taussig", "marfan",
            "ebstein", "eisenmenger", "kawasaki", "takayasu", "dressler", "fallot",
            "waterston", "jatene", "rastelli", "yacoub", "carpentier", "senning", "bjork", "shiley"
        )

        /** Disease/term names derived from a place that a place detector can mistake for a real location. Same list as iOS. */
        val geographicMedicalTermAllowlist = setOf(
            "lyme", "west nile", "rocky mountain", "german", "japanese",
            "norwegian", "marburg", "ebola", "zika", "spanish", "asian",
            "hong kong", "legionnaires", "legionnaires'", "middle east",
            "mediterranean", "angina"
        )

        /**
         * A run of capitalized words immediately followed by a common
         * institutional-name keyword. Generic mentions ("the hospital", "an
         * outside hospital") don't match since they aren't capitalized.
         */
        val institutionPattern = Regex(
            """\b(?:[A-Z][A-Za-z'.]*\s+){1,6}(?:Hospital|Clinic|Medical Center|Health System|Infirmary|Institute|Health Center)\b"""
        )

        /** "Dr. Smith", "Mrs. Alvarez-Cole", "Prof Jane Doe" -- a title is a reliable cue even when the model misses the name. */
        val titledNamePattern = Regex(
            """\b(?:Mr|Mrs|Ms|Miss|Mx|Dr|Prof)\.?\s+((?:[A-Z][A-Za-z'\u2019\-]+)(?:\s+[A-Z][A-Za-z'\u2019\-]+)?)"""
        )

        /**
         * "named john smith", "called Maria" -- an explicit introduction is a
         * reliable cue even when the name is typed in lowercase, which the
         * models (trained on capitalized news text) can't see.
         */
        val introducedNamePattern = Regex(
            """\b(?:named|called|name is|name's)\s+([A-Za-z][A-Za-z'\u2019\-]+(?:\s+[A-Za-z][A-Za-z'\u2019\-]+)?)""",
            RegexOption.IGNORE_CASE
        )

        /** Words that end an introduced name ("named john smith has ..."), so a second word is only taken if it isn't one of these. */
        val nameTerminators = setOf(
            "has", "had", "have", "is", "was", "who", "with", "and", "presents", "presented", "a", "an", "the",
            "in", "from", "of", "for", "to", "on", "at", "by", "after", "before", "also", "now", "here", "there"
        )

        /** A US state abbreviation directly after a detected place ("Houston, TX") belongs to the same location. */
        val trailingStateCode = Regex(
            """^,?\s+(?:A[KLRZ]|C[AOT]|D[CE]|FL|GA|HI|I[ADLN]|K[SY]|LA|M[ADEINOST]|N[CDEHJMVY]|O[HKR]|PA|RI|S[CD]|T[NX]|UT|V[AT]|W[AIVY])\b"""
        )

        val wordPattern = Regex("""[A-Za-z][A-Za-z'\u2019\-]*""")

        val addressPattern = Regex(
            """\b\d{1,5}\s+(?:[A-Z][a-z]+\s+){1,3}(?:Street|St|Avenue|Ave|Road|Rd|Boulevard|Blvd|Drive|Dr|Lane|Ln|Court|Ct|Way|Place|Pl)\b\.?"""
        )

        const val MONTH =
            """(?:Jan(?:uary)?|Feb(?:ruary)?|Mar(?:ch)?|Apr(?:il)?|May|Jun(?:e)?|Jul(?:y)?|Aug(?:ust)?|Sep(?:t(?:ember)?)?|Oct(?:ober)?|Nov(?:ember)?|Dec(?:ember)?)"""

        val datePatterns = listOf(
            // March 5, 2021 / Mar. 5th / March 5
            Regex("""\b$MONTH\.?\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{4})?\b"""),
            // 5 March 2021 / 5th of March
            Regex("""\b\d{1,2}(?:st|nd|rd|th)?\s+(?:of\s+)?$MONTH\.?(?:,?\s+\d{4})?\b"""),
            // March 2021
            Regex("""\b$MONTH\.?,?\s+\d{4}\b"""),
            // 3/5/2021, 03-05-21, 2021-03-05
            Regex("""\b\d{1,4}[/-]\d{1,2}[/-]\d{2,4}\b"""),
            // "in 2019", "since 2021" -- a bare year is still a date element
            Regex("""\b(?:in|since|during|from|until|of|by)\s+(?:19|20)\d{2}\b""")
        )
    }
}

/**
 * The app-wide filter. FAILS CLOSED: if the models can't be loaded, [redact]
 * throws and callers must not send the text anywhere. Loads the bundled models and word list from assets on
 * first use (a second or so on a mid-range phone), so call [warmUp] from a
 * background thread at startup and keep [redact] off the main thread.
 */
object PHIFilterService {
    private val filter: PHIFilter by lazy {
        val assets = Parse.getApplicationContext().assets
        val words = HashSet<String>(60_000)
        listOf("phi/MedicalDictionary.txt", "phi/CVTMedicalDictionary.txt").forEach { name ->
            assets.open(name).bufferedReader().useLines { lines ->
                lines.forEach { line -> line.trim().takeIf { it.isNotEmpty() }?.let { words += it.lowercase() } }
            }
        }
        PHIFilter(assets.open("phi/en-ner-person.bin"), assets.open("phi/en-ner-location.bin"), words)
    }

    fun warmUp() {
        filter
    }

    fun redact(text: String): PHIFilterResult =
        try {
            filter.redact(text)
        } catch (e: Throwable) {
            // Callers fail closed on any exception; log the cause so it's diagnosable.
            Log.e("PHIFilter", "PHI screening failed", e)
            throw e
        }
}
