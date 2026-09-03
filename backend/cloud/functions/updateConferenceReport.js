const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { ValidationError, toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

function requirePlainObject(value, fieldName) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new ValidationError(`${fieldName} is required and must be an object.`);
  }
  return value;
}

Parse.Cloud.define('cscUpdateConferenceReport', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');
    const report = requirePlainObject(params.report, 'report');

    const caseState = await conferenceCaseService.updateReport({ caseId, ownerId, report });

    logger.info({
      function: 'cscUpdateConferenceReport',
      caseId,
      latencyMs: Date.now() - startedAt,
    });

    return conferenceCaseService.formatFinalizedCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscUpdateConferenceReport', caseId: params.caseId, message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
