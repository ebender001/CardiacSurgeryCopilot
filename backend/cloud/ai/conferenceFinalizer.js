/**
 * Responsible for generating the ten-section preoperative case conference
 * report (as nine content sections plus pending evidence/guideline
 * reference topics) once information collection is complete.
 */
const aiService = require('../services/aiService');
const { FINALIZE_CONFERENCE_PROMPT_VERSION, buildFinalizeConferencePrompt } = require('../prompts/finalizeConferencePrompt');
const { validateFinalizeConferenceCaseResponse } = require('../schemas/conferenceResponseSchema');
const referenceService = require('../services/referenceService');

/**
 * `heartTeamResponses`/`heartTeamEvidence` are optional and passed through
 * as-is from the case (see conferenceCaseService.finalizeCase) -- a report
 * can be finalized whether or not heart-team responses/evidence exist yet
 * for this case; the "preponderanceOfEvidence" section is instructed to
 * say so plainly rather than this requiring them.
 */
async function finalizeCase({ extractedCase, conversation, originalNarrative, heartTeamResponses, heartTeamEvidence, caseId }) {
  const { system, user } = buildFinalizeConferencePrompt({ extractedCase, conversation, originalNarrative, heartTeamResponses, heartTeamEvidence });
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
    preponderanceOfEvidence: result.preponderanceOfEvidence,
    evidenceGuidelines,
    meta,
    promptVersion: FINALIZE_CONFERENCE_PROMPT_VERSION,
  };
}

module.exports = { finalizeCase };
