const {
  validateHeartTeamResponsesResponse,
  ROLES,
  CLASS_OF_RECOMMENDATION_VALUES,
  LEVEL_OF_EVIDENCE_VALUES,
} = require('../cloud/schemas/heartTeamResponseSchema');
const { AIResponseError } = require('../cloud/utils/errors');

function fullRole(overrides = {}) {
  return {
    recommendation: 'Proceed with SAVR.',
    rationale: 'Durability favors surgery for this patient.',
    classOfRecommendation: 'I',
    levelOfEvidence: 'B-NR',
    ...overrides,
  };
}

function fullResponse(overrides = {}) {
  return {
    surgeon: fullRole(),
    nonInterventionalCardiologist: fullRole(),
    interventionalCardiologist: fullRole(),
    ...overrides,
  };
}

describe('validateHeartTeamResponsesResponse', () => {
  it('accepts every valid COR/LOE combination', () => {
    const result = validateHeartTeamResponsesResponse(fullResponse());
    for (const role of ROLES) {
      expect(result[role].classOfRecommendation).toBe('I');
      expect(result[role].levelOfEvidence).toBe('B-NR');
    }
  });

  it.each(CLASS_OF_RECOMMENDATION_VALUES)('accepts classOfRecommendation "%s"', (value) => {
    const result = validateHeartTeamResponsesResponse(fullResponse({ surgeon: fullRole({ classOfRecommendation: value }) }));
    expect(result.surgeon.classOfRecommendation).toBe(value);
  });

  it.each(LEVEL_OF_EVIDENCE_VALUES)('accepts levelOfEvidence "%s"', (value) => {
    const result = validateHeartTeamResponsesResponse(fullResponse({ surgeon: fullRole({ levelOfEvidence: value }) }));
    expect(result.surgeon.levelOfEvidence).toBe(value);
  });

  it('normalizes an invalid classOfRecommendation to null rather than throwing', () => {
    const result = validateHeartTeamResponsesResponse(fullResponse({ surgeon: fullRole({ classOfRecommendation: 'IV' }) }));
    expect(result.surgeon.classOfRecommendation).toBeNull();
  });

  it('normalizes an invalid levelOfEvidence to null rather than throwing', () => {
    const result = validateHeartTeamResponsesResponse(fullResponse({ surgeon: fullRole({ levelOfEvidence: 'D' }) }));
    expect(result.surgeon.levelOfEvidence).toBeNull();
  });

  it('normalizes missing grades to null without affecting recommendation/rationale', () => {
    const { classOfRecommendation, levelOfEvidence, ...ungraded } = fullRole();
    const result = validateHeartTeamResponsesResponse(fullResponse({ surgeon: ungraded }));
    expect(result.surgeon.classOfRecommendation).toBeNull();
    expect(result.surgeon.levelOfEvidence).toBeNull();
    expect(result.surgeon.recommendation).toBe('Proceed with SAVR.');
  });

  it('still requires recommendation and rationale regardless of grading', () => {
    expect(() =>
      validateHeartTeamResponsesResponse(fullResponse({ surgeon: fullRole({ recommendation: '' }) }))
    ).toThrow(AIResponseError);
  });
});
