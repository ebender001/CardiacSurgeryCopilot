const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

Parse.Cloud.define('cscAnswerConferenceQuestion', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const caseId = requireNonEmptyString(params.caseId, 'caseId');
    const questionId = requireNonEmptyString(params.questionId, 'questionId');
    const answer = requireNonEmptyString(params.answer, 'answer');

    const caseState = await conferenceCaseService.answerQuestion({ caseId, questionId, answer, ownerId });

    logger.info({
      function: 'cscAnswerConferenceQuestion',
      caseId: caseState.objectId,
      status: caseState.status,
      latencyMs: Date.now() - startedAt,
    });

    return conferenceCaseService.formatCaseSummary(caseState);
  } catch (err) {
    logger.error({ function: 'cscAnswerConferenceQuestion', caseId: params.caseId, message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
