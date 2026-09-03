const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscFinalizeConferenceCase', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');

    const caseState = await conferenceCaseService.finalizeCase({ caseId, ownerId });

    logger.info({
      function: 'cscFinalizeConferenceCase',
      caseId: caseState.objectId,
      latencyMs: Date.now() - startedAt,
    });

    return conferenceCaseService.formatFinalizedCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscFinalizeConferenceCase', caseId: params.caseId, message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
