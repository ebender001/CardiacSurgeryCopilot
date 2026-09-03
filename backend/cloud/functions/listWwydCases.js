const wwydCaseService = require('../services/wwydCaseService');
const { requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscListWwydCases', async (request) => {
  try {
    const ownerId = requireAuthenticatedUser(request);
    return await wwydCaseService.listCases({ ownerId });
  } catch (err) {
    logger.error({ function: 'cscListWwydCases', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
