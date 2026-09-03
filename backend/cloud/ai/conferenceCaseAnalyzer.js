/**
 * Responsible for interpreting the trainee's narrative and maintaining the
 * structured extractedCase as new answers arrive. Does NOT decide whether
 * another question is needed (see ai/conferenceQuestionGenerator.js) and
 * does NOT generate the final report (see ai/conferenceFinalizer.js).
 */
const aiService = require('../services/aiService');
const {
  ANALYZE_CONFERENCE_CASE_PROMPT_VERSION,
  buildInitialAnalysisPrompt,
  buildIncorporateAnswerPrompt,
} = require('../prompts/analyzeConferenceCasePrompt');
const { validateAnalyzeConferenceCaseResponse } = require('../schemas/conferenceResponseSchema');

async function analyzeInitialNarrative({ narrative, caseId }) {
  const { system, user } = buildInitialAnalysisPrompt(narrative);
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    operation: 'analyzeInitialConferenceCase',
    caseId,
  });
  const result = validateAnalyzeConferenceCaseResponse(data);
  return { ...result, meta, promptVersion: ANALYZE_CONFERENCE_CASE_PROMPT_VERSION };
}

async function incorporateAnswer({ extractedCase, conversation, newEntry, caseId }) {
  const { system, user } = buildIncorporateAnswerPrompt({ extractedCase, conversation, newEntry });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    operation: 'incorporateConferenceAnswer',
    caseId,
  });
  const result = validateAnalyzeConferenceCaseResponse(data);
  return { ...result, meta, promptVersion: ANALYZE_CONFERENCE_CASE_PROMPT_VERSION };
}

module.exports = { analyzeInitialNarrative, incorporateAnswer };
