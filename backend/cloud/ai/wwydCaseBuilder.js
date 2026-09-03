/**
 * Responsible for turning a trainee's raw case narrative into a WWYD case
 * opening (short presentation + starting question). One-shot -- unlike
 * the Conference workflow's caseAnalyzer, there is no incorporateAnswer
 * loop here.
 */
const aiService = require('../services/aiService');
const { WWYD_CASE_BUILDER_PROMPT_VERSION, buildWwydCaseBuilderPrompt } = require('../prompts/wwydCaseBuilderPrompt');
const { validateWwydCaseBuilderResponse } = require('../schemas/wwydResponseSchema');

async function buildCase({ narrative, caseId }) {
  const { system, user } = buildWwydCaseBuilderPrompt({ narrative });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    temperature: 0.3,
    operation: 'buildWwydCase',
    caseId,
  });
  const result = validateWwydCaseBuilderResponse(data);
  return { ...result, meta, promptVersion: WWYD_CASE_BUILDER_PROMPT_VERSION };
}

module.exports = { buildCase };
