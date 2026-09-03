const wwydCaseService = require('../services/wwydCaseService');
const { requireMeaningfulNarrative, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscCreateWwydCase', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const narrative = requireMeaningfulNarrative(params.narrative);
    const caseState = await wwydCaseService.createCase({ narrative, ownerId });

    logger.info({
      function: 'cscCreateWwydCase',
      caseId: caseState.objectId,
      latencyMs: Date.now() - startedAt,
    });

    return wwydCaseService.formatCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscCreateWwydCase', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
