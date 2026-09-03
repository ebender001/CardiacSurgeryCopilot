const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscGetConferenceCase', async (request) => {
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');

    const caseState = await conferenceCaseService.getCase({ caseId, ownerId });
    return conferenceCaseService.formatFullCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscGetConferenceCase', caseId: params.caseId, message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
