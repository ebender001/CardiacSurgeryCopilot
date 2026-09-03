const accountService = require('../services/accountService');
const { requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

/**
 * Permanently deletes the caller's own account and all of their case data
 * across both workflows -- in-app account deletion (Apple App Store Review
 * Guideline 5.1.1(v)). Deliberately takes no params beyond the
 * authenticated user: there is no legitimate reason for this to ever
 * target another account.
 */
Parse.Cloud.define('cscDeleteAccount', async (request) => {
  try {
    const userId = requireAuthenticatedUser(request);
    await accountService.deleteAccount(userId);

    return { deleted: true };
  } catch (err) {
    logger.error({ function: 'cscDeleteAccount', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
