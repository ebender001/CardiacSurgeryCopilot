const { AIResponseError } = require('../utils/errors');

/** Validates the { query } shape returned by ai/referenceQueryBuilder.js. */
function validateReferenceQueryResponse(data) {
  if (!data || typeof data.query !== 'string' || data.query.trim().length === 0) {
    throw new AIResponseError('AI response for buildReferenceQuery is missing a usable query string.');
  }
  return { query: data.query.trim() };
}

module.exports = { validateReferenceQueryResponse };
