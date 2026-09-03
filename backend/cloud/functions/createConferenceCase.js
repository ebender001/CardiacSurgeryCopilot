const conferenceCaseService = require('../services/conferenceCaseService');
const { requireMeaningfulNarrative, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscCreateConferenceCase', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const narrative = requireMeaningfulNarrative(params.narrative);
    const caseState = await conferenceCaseService.createCase({ narrative, ownerId });

    logger.info({
      function: 'cscCreateConferenceCase',
      caseId: caseState.objectId,
      status: caseState.status,
      latencyMs: Date.now() - startedAt,
    });

    return conferenceCaseService.formatCaseSummary(caseState);
  } catch (err) {
    logger.error({ function: 'cscCreateConferenceCase', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
