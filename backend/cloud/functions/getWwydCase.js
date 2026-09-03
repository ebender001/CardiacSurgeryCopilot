const wwydCaseService = require('../services/wwydCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscGetWwydCase', async (request) => {
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');

    const caseState = await wwydCaseService.getCase({ caseId, ownerId });

    return wwydCaseService.formatFullCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscGetWwydCase', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
