const { CONFERENCE_EDUCATOR_PERSONA } = require('./persona');

const FINALIZE_CONFERENCE_PROMPT_VERSION = '1.1.0';

/**
 * Builds the prompt used once information collection is complete, to
 * generate the nine-section preoperative case conference report.
 */
function buildFinalizeConferencePrompt({ extractedCase, conversation, originalNarrative }) {
  const qaSummary = conversation
    .filter((entry) => entry.answer)
    .map((entry) => `Q: ${entry.question}\nA: ${entry.answer}`)
    .join('\n\n');

  const system = `${CONFERENCE_EDUCATOR_PERSONA}

The trainee has finished gathering information for this preoperative cardiac surgery case. Produce the report they need to present and discuss at a multidisciplinary heart team conference -- cardiologists (interventional and non-interventional) and other specialists will be there alongside surgeons, and the report should hold up to their questions, not just a surgeon's. Each section may be written as prose or as an array of short bullet strings -- pick whichever reads more clearly for that section's content, no need to force a consistent shape across sections.

Generate exactly these sections:

1. "diagnosis": the working cardiac diagnosis.
2. "indication": why surgery -- rather than a non-surgical or catheter-based alternative, or continued medical management -- is indicated for this patient.
3. "missingInformation": clinically relevant gaps or assumptions that remain even after the trainee's answers -- things to confirm before or at the conference. If nothing meaningful is missing, say so explicitly rather than omitting this section.
4. "operativeStrategy": the planned operative approach.
5. "alternatives": other reasonable approaches and why the planned strategy was chosen over them -- explicitly including non-surgical or catheter-based options a cardiologist on the heart team would expect to see addressed (e.g. optimal medical therapy, PCI vs. CABG, TAVR vs. SAVR, a staged or hybrid approach), not only alternative surgical techniques.
6. "controversies": where reasonable, well-informed heart-team members might disagree about this case's management -- including cross-specialty disagreement (e.g. a cardiologist favoring a less invasive option, differing views on risk tolerance or timing), not only disagreement among surgeons.
7. "technicalConsiderations": anatomy- or technique-specific notes relevant to the planned operation.
8. "postoperativeConcerns": risks or recovery issues to anticipate and plan for.

Also generate:
9. "referenceTopics": an array of topics where the trainee would benefit from supporting literature or guidelines, each with "topic" and "searchIntent". Do NOT fabricate citations, PubMed IDs, DOIs, authors, journals, or publication dates -- only describe what should be looked up later.

Do not fabricate any clinical detail that was not provided or clearly implied. Do not assign blame -- this is preparation, not review.

Respond only with a JSON object of the form:
{
  "diagnosis": "...",
  "indication": "...",
  "missingInformation": "...",
  "operativeStrategy": "...",
  "alternatives": "...",
  "controversies": "...",
  "technicalConsiderations": "...",
  "postoperativeConcerns": "...",
  "referenceTopics": [ { "topic": "...", "searchIntent": "..." } ]
}`;

  const user = `Original dictated/typed narrative:
"""
${originalNarrative}
"""

Structured case information:
${JSON.stringify(extractedCase, null, 2)}

Follow-up questions and answers collected:
${qaSummary || '(none were needed)'}`;

  return { system, user };
}

module.exports = { FINALIZE_CONFERENCE_PROMPT_VERSION, buildFinalizeConferencePrompt };
