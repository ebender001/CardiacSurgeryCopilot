/**
 * Validates and sanitizes JSON returned by the AI provider for the What
 * Would You Do workflow before it is ever stored or returned to a client.
 * AI output is never trusted as-is.
 */
const { AIResponseError } = require('../utils/errors');

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function validateWwydCaseBuilderResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('WWYD case-builder response was not a JSON object.');
  }
  if (!isNonEmptyString(data.casePresentation)) {
    throw new AIResponseError('WWYD case-builder response is missing "casePresentation".');
  }
  if (!isNonEmptyString(data.startingQuestion)) {
    throw new AIResponseError('WWYD case-builder response is missing "startingQuestion".');
  }
  return {
    casePresentation: data.casePresentation.trim(),
    startingQuestion: data.startingQuestion.trim(),
  };
}

function validateWwydConversationResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('WWYD conversation response was not a JSON object.');
  }
  if (!isNonEmptyString(data.reply)) {
    throw new AIResponseError('WWYD conversation response is missing "reply".');
  }
  return { reply: data.reply.trim() };
}

module.exports = { validateWwydCaseBuilderResponse, validateWwydConversationResponse };
