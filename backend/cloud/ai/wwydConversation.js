/**
 * Responsible for one turn of the live WWYD discussion: given the case
 * opening, the conversation so far, and the trainee's new message,
 * produces the attending's next reply. Does not touch wwydCaseRepository
 * -- wwydCaseService is the one that fetches/owns the case, appends
 * messages, and records AI usage.
 */
const aiService = require('../services/aiService');
const { WWYD_CONVERSATION_PROMPT_VERSION, buildWwydConversationPrompt } = require('../prompts/wwydConversationPrompt');
const { validateWwydConversationResponse } = require('../schemas/wwydResponseSchema');

async function continueConversation({ casePresentation, startingQuestion, conversation, newMessage, caseId }) {
  const { system, user } = buildWwydConversationPrompt({ casePresentation, startingQuestion, conversation, newMessage });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    temperature: 0.4,
    operation: 'continueWwydConversation',
    caseId,
  });
  const result = validateWwydConversationResponse(data);
  return { ...result, meta, promptVersion: WWYD_CONVERSATION_PROMPT_VERSION };
}

module.exports = { continueConversation };
