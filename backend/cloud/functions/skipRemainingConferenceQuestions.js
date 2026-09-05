const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

/**
 * Lets the trainee deliberately stop answering follow-up questions early
 * (see ConferenceInterviewView's "Stop Asking Questions" action) rather
 * than continue until the AI itself decides enough is known. The client
 * is responsible for warning the trainee that the case analysis may be
 * less reliable without the skipped information before calling this --
 * this function itself does not ask for confirmation.
 */
Parse.Cloud.define('cscSkipRemainingConferenceQuestions', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');

    const caseState = await conferenceCaseService.skipRemainingQuestions({ caseId, ownerId });

    logger.info({
      function: 'cscSkipRemainingConferenceQuestions',
      caseId: caseState.objectId,
      status: caseState.status,
      latencyMs: Date.now() - startedAt,
    });

    return conferenceCaseService.formatCaseSummary(caseState);
  } catch (err) {
    logger.error({ function: 'cscSkipRemainingConferenceQuestions', caseId: params.caseId, message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
