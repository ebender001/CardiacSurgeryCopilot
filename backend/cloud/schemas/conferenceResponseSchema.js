/**
 * Validates and sanitizes JSON returned by the AI provider for the
 * Preoperative Case Conference workflow before it is ever stored or
 * returned to a client. AI output is never trusted as-is.
 */
const { AIResponseError } = require('../utils/errors');
const { sanitizeExtractedCase } = require('./conferenceExtractedCaseSchema');

function isNonEmptyString(value) {
  return typeof value === 'string' && value.trim().length > 0;
}

function validateAnalyzeConferenceCaseResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('Case analysis response was not a JSON object.');
  }
  if (typeof data.extractedCase !== 'object' || data.extractedCase === null || Array.isArray(data.extractedCase)) {
    throw new AIResponseError('Case analysis response is missing a valid extractedCase object.');
  }
  return { extractedCase: sanitizeExtractedCase(data.extractedCase) };
}

function validateQuestionShape(question, context) {
  if (!question || typeof question !== 'object' || Array.isArray(question)) {
    throw new AIResponseError(`${context} is missing a valid question object.`);
  }
  if (!isNonEmptyString(question.text)) {
    throw new AIResponseError(`${context} question is missing "text".`);
  }
  if (!isNonEmptyString(question.category)) {
    throw new AIResponseError(`${context} question is missing "category".`);
  }
  if (!isNonEmptyString(question.reason)) {
    throw new AIResponseError(`${context} question is missing "reason".`);
  }
  return {
    text: question.text.trim(),
    category: question.category.trim(),
    reason: question.reason.trim(),
  };
}

function validateNextConferenceQuestionResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('Next-question response was not a JSON object.');
  }
  if (typeof data.needsQuestion !== 'boolean') {
    throw new AIResponseError('Next-question response is missing a "needsQuestion" boolean.');
  }
  if (!data.needsQuestion) {
    return { needsQuestion: false, question: null };
  }
  return { needsQuestion: true, question: validateQuestionShape(data.question, 'Next-question response') };
}

/**
 * Each of the report's eight content sections is accepted as either a
 * non-empty string (prose) or a non-empty array of non-empty strings
 * (bullet points) -- the AI may reasonably produce either shape depending
 * on the section. Whichever shape comes back, this normalizes it to a
 * single string (array items joined with newlines) so the stored report
 * and every client always see one consistent shape per section, rather
 * than the client having to branch on string-vs-array per field.
 */
function normalizeSection(value, fieldName) {
  if (isNonEmptyString(value)) {
    return value.trim();
  }
  if (Array.isArray(value)) {
    const items = value.filter(isNonEmptyString).map((item) => item.trim());
    if (items.length > 0) {
      return items.join('\n');
    }
  }
  throw new AIResponseError(`Finalize response is missing "${fieldName}".`);
}

function validateFinalizeConferenceCaseResponse(data) {
  if (!data || typeof data !== 'object' || Array.isArray(data)) {
    throw new AIResponseError('Finalize response was not a JSON object.');
  }

  const sections = {
    diagnosis: normalizeSection(data.diagnosis, 'diagnosis'),
    indication: normalizeSection(data.indication, 'indication'),
    missingInformation: normalizeSection(data.missingInformation, 'missingInformation'),
    operativeStrategy: normalizeSection(data.operativeStrategy, 'operativeStrategy'),
    alternatives: normalizeSection(data.alternatives, 'alternatives'),
    controversies: normalizeSection(data.controversies, 'controversies'),
    technicalConsiderations: normalizeSection(data.technicalConsiderations, 'technicalConsiderations'),
    postoperativeConcerns: normalizeSection(data.postoperativeConcerns, 'postoperativeConcerns'),
  };

  const referenceTopics = Array.isArray(data.referenceTopics)
    ? data.referenceTopics
        .filter((ref) => ref && typeof ref === 'object' && isNonEmptyString(ref.topic))
        .map((ref) => ({
          topic: ref.topic.trim(),
          searchIntent: isNonEmptyString(ref.searchIntent) ? ref.searchIntent.trim() : '',
        }))
    : [];

  return { ...sections, referenceTopics };
}

module.exports = {
  validateAnalyzeConferenceCaseResponse,
  validateNextConferenceQuestionResponse,
  validateFinalizeConferenceCaseResponse,
};
