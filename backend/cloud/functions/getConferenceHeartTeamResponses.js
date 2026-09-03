const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

/**
 * Returns (generating and caching on first call) the case's three
 * heart-team-member responses -- see
 * services/conferenceCaseService.js#getHeartTeamResponses.
 */
Parse.Cloud.define('cscGetConferenceHeartTeamResponses', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');

    const responses = await conferenceCaseService.getHeartTeamResponses({ caseId, ownerId });

    logger.info({
      function: 'cscGetConferenceHeartTeamResponses',
      caseId,
      latencyMs: Date.now() - startedAt,
    });

    return { caseId, ...responses };
  } catch (err) {
    logger.error({ function: 'cscGetConferenceHeartTeamResponses', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
