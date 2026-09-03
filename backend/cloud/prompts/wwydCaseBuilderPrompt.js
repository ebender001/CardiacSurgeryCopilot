const { WWYD_ATTENDING_PERSONA } = require('./persona');

const WWYD_CASE_BUILDER_PROMPT_VERSION = '1.0.0';

/**
 * Builds the prompt used to turn a trainee's real, freshly dictated/typed
 * case into a short "What Would You Do" opening: a crisp presentation the
 * attending would give to frame the discussion, plus one good opening
 * question to kick it off. This runs once per case, up front -- unlike the
 * Conference workflow there is no follow-up-question loop first, since the
 * goal here is a fast discussion prompt, not a complete structured record.
 */
function buildWwydCaseBuilderPrompt({ narrative }) {
  const system = `${WWYD_ATTENDING_PERSONA}

The trainee has just told you about a real case they encountered. Before you start quizzing them, do two things:

1. Condense what they told you into a short case presentation (3-6 sentences) -- the way you'd frame it to open a teaching discussion. Use only facts the trainee actually stated or clearly implied. Never invent vital signs, lab values, imaging findings, or history that weren't given.
2. Write ONE good opening discussion question that starts the conversation at the case's actual decision point -- not a generic "what would you do?" but something specific to what makes this case worth discussing. This is the first thing you'll ask the trainee, so it should invite them to reason, not just recall a fact.

Respond only with a JSON object of this exact shape, no other text:
{ "casePresentation": "<the short case presentation>", "startingQuestion": "<your one opening question>" }`;

  const user = `Trainee's case, as dictated/typed:
"""
${narrative}
"""`;

  return { system, user };
}

module.exports = { WWYD_CASE_BUILDER_PROMPT_VERSION, buildWwydCaseBuilderPrompt };
