/**
 * Responsible for deciding whether another follow-up question is needed
 * and, if so, authoring the single highest-value question. Does not touch
 * extractedCase directly -- it only reads it.
 */
const aiService = require('../services/aiService');
const { NEXT_CONFERENCE_QUESTION_PROMPT_VERSION, buildNextQuestionPrompt } = require('../prompts/nextConferenceQuestionPrompt');
const { validateNextConferenceQuestionResponse } = require('../schemas/conferenceResponseSchema');

async function generateNextQuestion({ extractedCase, conversation, caseId }) {
  const { system, user } = buildNextQuestionPrompt({ extractedCase, conversation });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    operation: 'generateNextConferenceQuestion',
    caseId,
  });
  const result = validateNextConferenceQuestionResponse(data);
  return { ...result, meta, promptVersion: NEXT_CONFERENCE_QUESTION_PROMPT_VERSION };
}

module.exports = { generateNextQuestion };
