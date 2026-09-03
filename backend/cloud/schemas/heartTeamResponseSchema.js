/**
 * Validates and sanitizes JSON returned by the AI provider for the Heart
 * Team Responses feature -- three distinct, in-character heart-team
 * member takes on a Conference case, generated once a case needs no more
 * follow-up questions (see ai/heartTeamResponses.js).
 */
const { AIResponseError } = require('../utils/errors');

const ROLES = ['surgeon', 'nonInterventionalCardiologist', 'interventionalCardiologist'];

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function validateRole(value, role) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) {
    throw new AIResponseError(`Heart team response is missing a valid "${role}" object.`);
  }
  if (!isNonEmptyString(value.recommendation)) {
    throw new AIResponseError(`Heart team response "${role}" is missing "recommendation".`);
  }
  if (!isNonEmptyString(value.rationale)) {
    throw new AIResponseError(`Heart team response "${role}" is missing "rationale".`);
  }
  return {
    recommendation: value.recommendation.trim(),
    rationale: value.rationale.trim(),
  };
}

function validateHeartTeamResponsesResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('Heart team responses were not a JSON object.');
  }

  const result = {};
  for (const role of ROLES) {
    result[role] = validateRole(data[role], role);
  }
  return result;
}

module.exports = { validateHeartTeamResponsesResponse, ROLES };
