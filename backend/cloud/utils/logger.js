/**
 * Minimal structured-ish logger for Back4App Cloud Code logs.
 *
 * Deliberately takes a fields object rather than free-form strings so log
 * lines stay greppable, e.g.:
 *   [CardiacSurgeryCopilot] function=cscAnswerConferenceQuestion caseId=abc123 operation=nextQuestion model=gpt-4o latencyMs=1240
 *
 * Never pass full narratives, answers, or AI-generated prose to this logger.
 */
function formatLine(fields) {
  return Object.entries(fields)
    .filter(([, value]) => value !== undefined && value !== null && value !== '')
    .map(([key, value]) => `${key}=${value}`)
    .join(' ');
}

function info(fields) {
  console.log(`[CardiacSurgeryCopilot] ${formatLine(fields)}`);
}

function warn(fields) {
  console.warn(`[CardiacSurgeryCopilot] ${formatLine(fields)}`);
}

function error(fields) {
  console.error(`[CardiacSurgeryCopilot] ${formatLine(fields)}`);
}

module.exports = { info, warn, error };
