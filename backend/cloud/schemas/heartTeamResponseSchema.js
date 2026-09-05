/**
 * Validates and sanitizes JSON returned by the AI provider for the Heart
 * Team Responses feature -- three distinct, in-character heart-team
 * member takes on a Conference case, generated once a case needs no more
 * follow-up questions (see ai/heartTeamResponses.js).
 */
const { AIResponseError } = require('../utils/errors');

const ROLES = ['surgeon', 'nonInterventionalCardiologist', 'interventionalCardiologist'];

/**
 * ACC/AHA-style Class of Recommendation / Level of Evidence grades (see
 * prompts/heartTeamResponsesPrompt.js for the rubric given to the AI).
 * Exported so the prompt builder quotes these exact tokens rather than a
 * second hand-typed copy drifting out of sync with what's actually valid.
 */
const CLASS_OF_RECOMMENDATION_VALUES = ['I', 'IIa', 'IIb', 'III-NoBenefit', 'III-Harm'];
const LEVEL_OF_EVIDENCE_VALUES = ['A', 'B-R', 'B-NR', 'C-LD', 'C-EO'];

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

/**
 * COR/LOE are supplementary grading on top of the load-bearing
 * recommendation/rationale, not required for the feature to function --
 * an invalid or missing grade normalizes to `null` (never shown on the
 * client) rather than failing the whole heart-team-responses call over a
 * malformed enum value on what's otherwise a good response.
 */
function validateGrade(value, validValues) {
  return typeof value === 'string' && validValues.includes(value) ? value : null;
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
    classOfRecommendation: validateGrade(value.classOfRecommendation, CLASS_OF_RECOMMENDATION_VALUES),
    levelOfEvidence: validateGrade(value.levelOfEvidence, LEVEL_OF_EVIDENCE_VALUES),
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

module.exports = {
  validateHeartTeamResponsesResponse,
  ROLES,
  CLASS_OF_RECOMMENDATION_VALUES,
  LEVEL_OF_EVIDENCE_VALUES,
};
