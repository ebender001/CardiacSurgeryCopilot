/**
 * Generates three distinct, in-character heart-team member responses to a
 * Conference case (surgeon, non-interventional cardiologist, aggressive
 * interventional cardiologist) -- see prompts/heartTeamResponsesPrompt.js
 * for why these three and why they're instructed to genuinely disagree.
 */
const aiService = require('../services/aiService');
const {
  HEART_TEAM_RESPONSES_PROMPT_VERSION,
  buildHeartTeamResponsesPrompt,
} = require('../prompts/heartTeamResponsesPrompt');
const { validateHeartTeamResponsesResponse } = require('../schemas/heartTeamResponseSchema');

async function generateResponses({ extractedCase, conversation, originalNarrative, caseId }) {
  const { system, user } = buildHeartTeamResponsesPrompt({ extractedCase, conversation, originalNarrative });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    temperature: 0.6,
    operation: 'generateHeartTeamResponses',
    caseId,
  });
  const result = validateHeartTeamResponsesResponse(data);
  return { ...result, meta, promptVersion: HEART_TEAM_RESPONSES_PROMPT_VERSION };
}

module.exports = { generateResponses };
