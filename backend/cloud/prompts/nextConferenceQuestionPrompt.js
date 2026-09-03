const { CONFERENCE_EDUCATOR_PERSONA } = require('./persona');

const NEXT_CONFERENCE_QUESTION_PROMPT_VERSION = '1.0.0';

/**
 * Builds the prompt used to decide whether one more follow-up question is
 * needed, and if so, what the single highest-value question is, before
 * this case is ready to become a preoperative conference report.
 */
function buildNextQuestionPrompt({ extractedCase, conversation }) {
  const askedSoFar = conversation
    .map((entry, idx) => {
      const answerLine = entry.answer ? `   A: ${entry.answer}` : '   (not yet answered)';
      return `${idx + 1}. Q: ${entry.question}\n${answerLine}`;
    })
    .join('\n');

  const system = `${CONFERENCE_EDUCATOR_PERSONA}

You are deciding whether the trainee needs to answer one more follow-up question before this case is ready for a preoperative case conference report.

Consider the case sufficiently complete once you can reasonably:
1. state the working diagnosis and the indication for surgery,
2. describe the comorbidities and risk factors relevant to operative risk,
3. describe prior cardiac history and interventions,
4. describe the imaging/labs that support the diagnosis and surgical plan,
5. anticipate the anatomical or technical considerations that will shape the operative strategy,
6. anticipate a meaningful discussion of alternatives and where reasonable surgeons might disagree.

Do not keep asking questions simply because more detail could theoretically be gathered. Only ask about something if it would materially change the operative plan or the conference discussion. A trainee presenting a case they genuinely don't have every detail for is normal -- unresolved gaps belong in the report's "Missing information" section, not an endless follow-up loop.

If another question is needed, choose the single highest-value missing item. Never re-ask about information already present in the structured case or the conversation below. Keep the question concise, specific to this case, and phrased the way a thoughtful attending would ask it.

Respond only with a JSON object of one of these two forms:
{ "needsQuestion": false, "question": null }
or
{ "needsQuestion": true, "question": { "text": "...", "category": "...", "reason": "..." } }

"category" should be a short lowercase label such as "history", "imaging", "labs", "anatomy", "risk factors", "prior intervention", or "patient goals" (or another concise label that fits). "reason" should briefly explain why this question matters for THIS case.`;

  const user = `Current structured case:
${JSON.stringify(extractedCase, null, 2)}

Questions already asked in this conversation:
${askedSoFar || '(none yet)'}`;

  return { system, user };
}

module.exports = { NEXT_CONFERENCE_QUESTION_PROMPT_VERSION, buildNextQuestionPrompt };
