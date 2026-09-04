const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

/**
 * Returns (generating and caching on first call) one heart-team role's
 * pro/con PubMed evidence -- see
 * services/conferenceCaseService.js#getRoleEvidence.
 */
Parse.Cloud.define('cscGetHeartTeamRoleEvidence', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');
    const role = requireNonEmptyString(params.role, 'role');

    const evidence = await conferenceCaseService.getRoleEvidence({ caseId, ownerId, role });

    logger.info({
      function: 'cscGetHeartTeamRoleEvidence',
      caseId,
      role,
      proCount: evidence.pro.results.length,
      conCount: evidence.con.results.length,
      latencyMs: Date.now() - startedAt,
    });

    return { caseId, role, pro: evidence.pro, con: evidence.con };
  } catch (err) {
    logger.error({ function: 'cscGetHeartTeamRoleEvidence', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
