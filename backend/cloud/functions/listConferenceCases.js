const conferenceCaseService = require('../services/conferenceCaseService');
const { requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscListConferenceCases', async (request) => {
  try {
    const ownerId = requireAuthenticatedUser(request);
    const cases = await conferenceCaseService.listCases({ ownerId });
    return { cases };
  } catch (err) {
    logger.error({ function: 'cscListConferenceCases', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
