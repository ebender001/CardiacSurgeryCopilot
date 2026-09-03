const { CONFERENCE_EDUCATOR_PERSONA } = require('./persona');

const HEART_TEAM_RESPONSES_PROMPT_VERSION = '1.0.0';

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

Respond only with a JSON object of the form:
{
  "surgeon": { "recommendation": "...", "rationale": "..." },
  "nonInterventionalCardiologist": { "recommendation": "...", "rationale": "..." },
  "interventionalCardiologist": { "recommendation": "...", "rationale": "..." }
}

"recommendation" is a short (one sentence) statement of what that specialist would do. "rationale" is their fuller reasoning in their own voice.`;

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
