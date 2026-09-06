/**
 * Responsible for generating the nine-section preoperative case conference
 * report (as eight content sections plus pending evidence/guideline
 * reference topics) once information collection is complete.
 */
const aiService = require('../services/aiService');
const { FINALIZE_CONFERENCE_PROMPT_VERSION, buildFinalizeConferencePrompt } = require('../prompts/finalizeConferencePrompt');
const { validateFinalizeConferenceCaseResponse } = require('../schemas/conferenceResponseSchema');
const referenceService = require('../services/referenceService');

async function finalizeCase({ extractedCase, conversation, originalNarrative, caseId }) {
  const { system, user } = buildFinalizeConferencePrompt({ extractedCase, conversation, originalNarrative });
  const { data, meta } = await aiService.completeJSON({
    system,
    user,
    temperature: 0.4,
    operation: 'finalizeConferenceCase',
    caseId,
  });
  const result = validateFinalizeConferenceCaseResponse(data);
  const evidenceGuidelines = referenceService.buildPendingReferences(result.referenceTopics);

  return {
    diagnosis: result.diagnosis,
    indication: result.indication,
    missingInformation: result.missingInformation,
    operativeStrategy: result.operativeStrategy,
    alternatives: result.alternatives,
    controversies: result.controversies,
    technicalConsiderations: result.technicalConsiderations,
    postoperativeConcerns: result.postoperativeConcerns,
    evidenceGuidelines,
    meta,
    promptVersion: FINALIZE_CONFERENCE_PROMPT_VERSION,
  };
}

module.exports = { finalizeCase };
