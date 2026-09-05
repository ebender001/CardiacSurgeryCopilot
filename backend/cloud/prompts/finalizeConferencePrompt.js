const { CONFERENCE_EDUCATOR_PERSONA } = require('./persona');

const FINALIZE_CONFERENCE_PROMPT_VERSION = '1.3.0';

const HEART_TEAM_ROLE_LABELS = {
  surgeon: 'SURGEON',
  nonInterventionalCardiologist: 'NON-INTERVENTIONAL CARDIOLOGIST',
  interventionalCardiologist: 'INTERVENTIONAL CARDIOLOGIST',
};

/**
 * Renders whatever heart-team responses/evidence already exist on the
 * case into the compact, citable summary the "preponderanceOfEvidence"
 * section is asked to reason from -- title/journal/year only (not full
 * abstracts), since that's enough for the AI to weigh recency/follow-up
 * framing without materially increasing prompt size or cost. Deliberately
 * distinguishes "no evidence reviewed for this role" from "reviewed and
 * found nothing" from "reviewed and found N articles" -- the
 * preponderance section needs to be honest about which of these it's
 * actually working from, not just told "here's some evidence."
 */
function formatArticleList(results) {
  if (!results) return '(not yet reviewed)';
  if (results.length === 0) return '(reviewed -- no supporting literature found)';
  return results.map((article) => `- "${article.title}" (${article.journal || 'journal unknown'}, ${article.year || 'year unknown'})`).join('\n');
}

function formatHeartTeamSummary(heartTeamResponses, heartTeamEvidence) {
  if (!heartTeamResponses) {
    return '(Heart-team member responses have not been generated for this case yet -- no positions or evidence to summarize.)';
  }
  return Object.entries(HEART_TEAM_ROLE_LABELS)
    .map(([role, label]) => {
      const response = heartTeamResponses[role];
      if (!response) return null;
      const evidence = heartTeamEvidence && heartTeamEvidence[role];
      return `${label}
Recommendation: ${response.recommendation}
Rationale: ${response.rationale}
Evidence reviewed supporting this recommendation:
${formatArticleList(evidence && evidence.pro && evidence.pro.results)}
Evidence reviewed favoring an alternative:
${formatArticleList(evidence && evidence.con && evidence.con.results)}`;
    })
    .filter(Boolean)
    .join('\n\n');
}

/**
 * Builds the prompt used once information collection is complete, to
 * generate the ten-section preoperative case conference report.
 */
function buildFinalizeConferencePrompt({ extractedCase, conversation, originalNarrative, heartTeamResponses, heartTeamEvidence }) {
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
5. "alternatives": other reasonable approaches and why the planned strategy was chosen over them -- explicitly including non-surgical or catheter-based options a cardiologist on the heart team would expect to see addressed (e.g. optimal medical therapy, PCI vs. CABG, TAVR vs. SAVR, a staged or hybrid approach), not only alternative surgical techniques. If the comparison turns on evidence with a much shorter follow-up than the decision horizon that actually matters (see your persona's guidance on this -- most importantly TAVR vs. SAVR durability), say so here rather than presenting a short-term result as though it settles the choice.
6. "controversies": where reasonable, well-informed heart-team members might disagree about this case's management -- including cross-specialty disagreement (e.g. a cardiologist favoring a less invasive option, differing views on risk tolerance or timing), not only disagreement among surgeons. A mismatch between trial follow-up duration and the patient's expected remaining lifetime is itself a legitimate controversy to name when relevant, not just a footnote to the alternatives section.
7. "technicalConsiderations": anatomy- or technique-specific notes relevant to the planned operation. When durability or reintervention is a live consideration for this patient (e.g. a younger or lower-risk patient facing a valve-choice decision), note what reintervention would look like if the chosen therapy fails or degenerates (e.g. redo surgery, valve-in-valve TAVR, TAVR explant) and the added risk each carries.
8. "postoperativeConcerns": risks or recovery issues to anticipate and plan for.
9. "preponderanceOfEvidence": below is whatever heart-team-member positions and pro/con literature review this case already has (possibly none, possibly partial). State plainly whether the totality of what's been reviewed leans toward one approach, is genuinely mixed/at odds (real clinical equipoise, not a cop-out), or is simply too thin to say -- and say which of these it is explicitly, don't leave the reader to infer it. Ground this only in the specific evidence listed below (cite it by title) and the case-specific reasoning already given by each role; never invent a study, a finding, or a weight-of-evidence conclusion the listed evidence doesn't actually support.
   - If no heart-team responses exist yet, or no role has any evidence reviewed, say exactly that -- there is nothing to weigh yet -- rather than synthesizing from the roles' unsupported opinions alone.
   - If only some roles have evidence reviewed, base the statement on those and explicitly name which role(s) haven't been reviewed yet, so the trainee knows the statement is partial, not complete.
   - If the reviewed evidence itself is genuinely mixed across roles, say so as the finding, not as a gap -- that is a legitimate, common, and honest answer for exactly the follow-up-duration-mismatch situations your persona instructions describe (e.g. short-term transcatheter non-inferiority vs. long-term surgical durability data).

Also generate:
10. "referenceTopics": an array of topics where the trainee would benefit from supporting literature or guidelines, each with "topic" and "searchIntent". If you raised a follow-up-duration or durability/reintervention concern above (see 5-7), include a topic specifically aimed at finding the longest-available-follow-up comparative data for that decision, with "searchIntent" saying so explicitly (e.g. "Longest-follow-up durability outcomes comparing TAVR and surgical bioprosthetic valves"), so it isn't left as an unsupported assertion. Do NOT fabricate citations, PubMed IDs, DOIs, authors, journals, or publication dates -- only describe what should be looked up later.

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
  "preponderanceOfEvidence": "...",
  "referenceTopics": [ { "topic": "...", "searchIntent": "..." } ]
}`;

  const user = `Original dictated/typed narrative:
"""
${originalNarrative}
"""

Structured case information:
${JSON.stringify(extractedCase, null, 2)}

Follow-up questions and answers collected:
${qaSummary || '(none were needed)'}

Heart-team member positions and evidence reviewed so far (for "preponderanceOfEvidence" -- see its instructions above for exactly how to handle missing or partial evidence):
${formatHeartTeamSummary(heartTeamResponses, heartTeamEvidence)}`;

  return { system, user };
}

module.exports = { FINALIZE_CONFERENCE_PROMPT_VERSION, buildFinalizeConferencePrompt };
