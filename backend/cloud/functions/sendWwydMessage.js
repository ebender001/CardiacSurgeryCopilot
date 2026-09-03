const wwydCaseService = require('../services/wwydCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscSendWwydMessage', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');
    const message = requireNonEmptyString(params.message, 'message');

    const caseState = await wwydCaseService.sendMessage({ caseId, ownerId, message });

    logger.info({
      function: 'cscSendWwydMessage',
      caseId,
      latencyMs: Date.now() - startedAt,
    });

    return wwydCaseService.formatCase(caseState);
  } catch (err) {
    logger.error({ function: 'cscSendWwydMessage', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
