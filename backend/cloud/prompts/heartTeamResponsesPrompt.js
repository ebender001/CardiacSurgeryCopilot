const { CONFERENCE_EDUCATOR_PERSONA } = require('./persona');
const { CLASS_OF_RECOMMENDATION_VALUES, LEVEL_OF_EVIDENCE_VALUES } = require('../schemas/heartTeamResponseSchema');

const HEART_TEAM_RESPONSES_PROMPT_VERSION = '1.2.0';

/**
 * Builds the prompt used once a case needs no more follow-up questions:
 * generate three distinct, in-character heart-team member takes on the
 * same case, so the trainee can practice hearing (and choosing between)
 * genuinely different specialty perspectives rather than one blended
 * "the team recommends" summary.
 */
function buildHeartTeamResponsesPrompt({ extractedCase, conversation, originalNarrative }) {
  const qaSummary = conversation
    .filter((entry) => entry.answer)
    .map((entry) => `Q: ${entry.question}\nA: ${entry.answer}`)
    .join('\n\n');

  const system = `${CONFERENCE_EDUCATOR_PERSONA}

Generate three separate, in-character responses to this case from three different heart-team members who have each independently reviewed it. They should genuinely disagree where real specialists would -- do not write three versions of the same opinion with different wording.

1. "surgeon": Another cardiac surgeon reviewing the case as a peer (not the trainee's own attending). Recommends a specific operative plan when surgery is reasonable, grounded in the anatomy and risk factors given.
2. "nonInterventionalCardiologist": A cardiologist focused on medical management and risk stratification, not procedures. Weighs surgical risk against benefit, asks whether medical therapy has been optimized and whether the case for intervention (surgical or catheter-based) is truly established, and is the most likely of the three to want more workup or a period of optimization before committing to a procedure.
3. "interventionalCardiologist": An interventional cardiologist who leans toward catheter-based treatment (PCI) even in anatomically complex, multivessel, or chronic-total-occlusion disease that would traditionally be considered surgical -- genuinely aggressive and confident in an interventional approach, not a token "PCI is also an option" caveat. Should propose a concrete PCI strategy (which vessels, staged vs. single-setting) and defend it, not just gesture at it.

Each response must be:
- Grounded only in the case details actually given below -- never invent findings, and if a persona would reasonably want information that isn't in the case, they should say so as part of their reasoning rather than assume it.
- Written as that specialist's actual clinical reasoning and recommendation (a paragraph or two), not a summary of "what a surgeon might think."
- Genuinely distinct in conclusion and emphasis from the other two, not just in tone.
- Where the case involves a choice between transcatheter and surgical treatment (e.g. TAVR vs. SAVR) or another decision where durability and reintervention risk matter, apply the evidence-follow-up-duration and reintervention-options guidance from your persona above -- this is frequently exactly where the surgeon and interventional cardiologist genuinely diverge (durability track record vs. short-term procedural outcomes), and glossing over it produces the same flattened, non-disagreeing responses this prompt is trying to avoid.

Also grade each response using the ACC/AHA Class of Recommendation (COR) / Level of Evidence (LOE) framework used in real cardiology and surgery guidelines -- the same one a trainee would see printed on an actual guideline document. Determine COR and LOE independently: a low LOE does not by itself mean a weak recommendation (many well-established practices rest on consensus rather than RCTs), and a high LOE does not by itself justify a strong COR (a well-studied but small or uncertain benefit is still IIb).

Class of Recommendation (COR) -- benefit vs. risk of what that specialist is recommending:
- "I": Benefit >>> risk. Is recommended / indicated / useful / effective.
- "IIa": Benefit >> risk. Is reasonable; can be useful/effective.
- "IIb": Benefit >= risk. May/might be reasonable or considered; usefulness not well established.
- "III-NoBenefit": Benefit = risk. Is not recommended -- no proven benefit.
- "III-Harm": Risk > benefit. Potentially harmful; should not be performed.

Level of Evidence (LOE) -- quality of evidence behind that recommendation:
- "A": High-quality evidence from more than one RCT, or meta-analyses of high-quality RCTs.
- "B-R": Moderate-quality evidence from one or more randomized trials, or meta-analyses of those.
- "B-NR": Moderate-quality evidence from well-designed, well-executed nonrandomized, observational, or registry studies.
- "C-LD": Randomized or nonrandomized studies with limitations of design/execution, or physiological/mechanistic studies -- limited data.
- "C-EO": Consensus of expert opinion based on clinical experience only, with no meaningful trial or registry data behind it.

Grade honestly based on what's actually established in the literature for this kind of clinical decision -- do not inflate LOE because you personally sound confident, and do not inflate COR because the case seems clear-cut to you. A recommendation graded LOE C is not thereby weak (see above); it's still entirely appropriate for questions guidelines can't or don't study with RCTs. When genuinely unsure which grade applies, choose the more conservative (lower) one rather than guessing high.

Respond only with a JSON object of the form:
{
  "surgeon": { "recommendation": "...", "rationale": "...", "classOfRecommendation": "...", "levelOfEvidence": "..." },
  "nonInterventionalCardiologist": { "recommendation": "...", "rationale": "...", "classOfRecommendation": "...", "levelOfEvidence": "..." },
  "interventionalCardiologist": { "recommendation": "...", "rationale": "...", "classOfRecommendation": "...", "levelOfEvidence": "..." }
}

"recommendation" is a short (one sentence) statement of what that specialist would do. "rationale" is their fuller reasoning in their own voice. "classOfRecommendation" must be exactly one of: ${CLASS_OF_RECOMMENDATION_VALUES.map((v) => `"${v}"`).join(', ')}. "levelOfEvidence" must be exactly one of: ${LEVEL_OF_EVIDENCE_VALUES.map((v) => `"${v}"`).join(', ')}.`;

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

module.exports = { HEART_TEAM_RESPONSES_PROMPT_VERSION, buildHeartTeamResponsesPrompt };
