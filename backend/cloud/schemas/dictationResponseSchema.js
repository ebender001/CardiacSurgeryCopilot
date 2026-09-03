const { AIResponseError } = require('../utils/errors');

/** Validates the { correctedSegment, changes } shape returned by ai/dictationCorrector.js. */
function validateCorrectDictationResponse(data) {
  if (!data || typeof data.correctedSegment !== 'string') {
    throw new AIResponseError('AI response for correctDictationSegment is missing correctedSegment.');
  }
  const rawChanges = Array.isArray(data.changes) ? data.changes : [];
  const changes = rawChanges
    .filter((c) => c && typeof c.original === 'string' && typeof c.corrected === 'string')
    .map((c) => ({ original: c.original, corrected: c.corrected }));
  return { correctedSegment: data.correctedSegment, changes };
}

module.exports = { validateCorrectDictationResponse };
