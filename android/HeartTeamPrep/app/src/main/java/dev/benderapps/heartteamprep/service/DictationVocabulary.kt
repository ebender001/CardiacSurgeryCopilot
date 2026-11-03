package dev.benderapps.heartteamprep.service

/**
 * Terms biased toward during on-device speech recognition so cardiac jargon
 * isn't misheard as an everyday word (e.g. "LAD" -> "LED"). Same curated
 * lists as the iOS client's `MedicalDictionaryService` -- kept short, since
 * recognizers weight a bounded phrase list best.
 */
object DictationVocabulary {
    /** Short abbreviations dictation frequently mishears as an unrelated, in-vocabulary word. */
    val commonAbbreviations = listOf(
        "LAD", "LCX", "LMCA", "RCA", "RCX", "CABG", "PCI", "PTCA",
        "MI", "STEMI", "NSTEMI", "CHF", "COPD", "DVT", "PE", "CVA", "TIA",
        "ICU", "ICD", "EKG", "ECG", "GERD", "IBD", "IBS", "UTI", "URI",
        "CAD", "HTN", "CKD", "AKI", "ESRD", "DKA", "ARDS", "GI", "GU",
        "ENT", "NPO", "PRN", "BID", "TID", "NSAID", "PPI", "ABG",
        "WBC", "RBC", "BUN", "INR", "PTT", "LVEF", "SAH", "SDH", "ICH"
    )

    /** Full terms/phrases confirmed, from real dictation sessions, to be repeatedly misheard. */
    val highRiskTerms = listOf(
        "saphenous", "mammary", "coronary",
        "saphenous vein graft", "internal mammary artery",
        "right coronary artery", "circumflex coronary artery"
    )

    val contextualStringSeed: List<String> = commonAbbreviations + highRiskTerms
}
