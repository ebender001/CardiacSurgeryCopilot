/**
 * Coordinates the Preoperative Case Conference workflow: creating cases,
 * incorporating answers, deciding when to stop asking questions, and
 * finalizing the nine-section report. This is the module Cloud Functions
 * call into -- it delegates extraction/question-generation/finalization to
 * ai/*, and persistence to conferenceCaseRepository.
 */
const conferenceCaseRepository = require('../repositories/conferenceCaseRepository');
const aiCostRepository = require('../repositories/aiCostRepository');
const conferenceCaseAnalyzer = require('../ai/conferenceCaseAnalyzer');
const conferenceQuestionGenerator = require('../ai/conferenceQuestionGenerator');
const conferenceFinalizer = require('../ai/conferenceFinalizer');
const heartTeamResponses = require('../ai/heartTeamResponses');
const { ConferenceCaseStatus } = require('../schemas/conferenceCaseStatus');
const { NotFoundError, InvalidStateError } = require('../utils/errors');
const { generateId } = require('../utils/idGenerator');
const { deriveTitle } = require('../utils/caseTitle');
const logger = require('../utils/logger');

const CASE_TYPE = 'conference';

/**
 * Records one AI provider call's cost/token usage against a case and
 * rolls it into the case's running total. A no-op if `meta.usage` is
 * missing (the provider didn't return one) -- callers still get their
 * case update either way, cost tracking is never allowed to block or
 * fail the actual workflow it's observing.
 */
async function recordAIUsage({ caseId, ownerId, operation, meta }) {
  if (!meta || !meta.usage) return;
  try {
    const { costUSD, totalTokens } = await aiCostRepository.record({
      caseId,
      caseType: CASE_TYPE,
      ownerId,
      operation,
      model: meta.model,
      usage: meta.usage,
      latencyMs: meta.latencyMs,
    });
    await conferenceCaseRepository.incrementAIUsage(caseId, { costUSD, totalTokens });
  } catch (err) {
    logger.error({ module: 'conferenceCaseService', operation: 'recordAIUsage', caseId, message: err.message });
  }
}

/**
 * Fetches a case and confirms `ownerId` owns it, in one step. A case that
 * exists but belongs to someone else is rejected the same way as one that
 * doesn't exist at all -- a case id alone must never reveal whether it
 * belongs to another user, let alone grant access to it.
 */
async function getOwnedCase(caseId, ownerId) {
  const caseState = await conferenceCaseRepository.getById(caseId);
  if (!caseState || caseState.ownerId !== ownerId) {
    throw new NotFoundError(`No conference case found with id ${caseId}.`);
  }
  return caseState;
}

function toNextQuestion(currentQuestion) {
  if (!currentQuestion) return null;
  return {
    id: currentQuestion.questionId,
    text: currentQuestion.question,
    category: currentQuestion.category,
    reason: currentQuestion.reason,
  };
}

/**
 * Runs the question generator against the case's current state and
 * persists either a new currentQuestion (status stays collecting) or a
 * transition to ready_to_finalize.
 */
async function applyQuestionOutcome(caseState, ownerId) {
  const questionResult = await conferenceQuestionGenerator.generateNextQuestion({
    extractedCase: caseState.extractedCase,
    conversation: caseState.conversation,
    caseId: caseState.objectId,
  });
  await recordAIUsage({
    caseId: caseState.objectId,
    ownerId,
    operation: 'generateNextConferenceQuestion',
    meta: questionResult.meta,
  });

  const promptVersion = {
    ...caseState.promptVersion,
    question: questionResult.promptVersion,
  };

  let update;
  if (questionResult.needsQuestion) {
    update = {
      status: ConferenceCaseStatus.COLLECTING_INFORMATION,
      currentQuestion: {
        questionId: generateId('q'),
        question: questionResult.question.text,
        category: questionResult.question.category,
        reason: questionResult.question.reason,
        answer: null,
        askedAt: new Date().toISOString(),
        answeredAt: null,
      },
      promptVersion,
      aiModel: questionResult.meta.model,
    };
  } else {
    update = {
      status: ConferenceCaseStatus.READY_TO_FINALIZE,
      currentQuestion: null,
      promptVersion,
      aiModel: questionResult.meta.model,
    };
  }

  const saved = await conferenceCaseRepository.update(caseState.objectId, update);
  logger.info({
    module: 'conferenceCaseService',
    caseId: saved.objectId,
    operation: 'applyQuestionOutcome',
    status: saved.status,
  });
  return saved;
}

async function createCase({ narrative, ownerId }) {
  const analysis = await conferenceCaseAnalyzer.analyzeInitialNarrative({ narrative, caseId: 'new' });

  const created = await conferenceCaseRepository.create({
    ownerId,
    status: ConferenceCaseStatus.COLLECTING_INFORMATION,
    originalNarrative: narrative,
    extractedCase: analysis.extractedCase,
    conversation: [],
    currentQuestion: null,
    report: null,
    referenceLookups: {},
    promptVersion: { analyze: analysis.promptVersion },
    aiModel: analysis.meta.model,
  });
  await recordAIUsage({
    caseId: created.objectId,
    ownerId,
    operation: 'analyzeInitialConferenceNarrative',
    meta: analysis.meta,
  });

  return applyQuestionOutcome(created, ownerId);
}

async function answerQuestion({ caseId, questionId, answer, ownerId }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.status !== ConferenceCaseStatus.COLLECTING_INFORMATION || !caseState.currentQuestion) {
    throw new InvalidStateError('This case is not currently awaiting an answer.');
  }
  if (caseState.currentQuestion.questionId !== questionId) {
    throw new InvalidStateError('That question is no longer the active question for this case.');
  }

  const answeredEntry = {
    ...caseState.currentQuestion,
    answer,
    answeredAt: new Date().toISOString(),
  };
  const conversation = [...caseState.conversation, answeredEntry];

  const incorporation = await conferenceCaseAnalyzer.incorporateAnswer({
    extractedCase: caseState.extractedCase,
    conversation,
    newEntry: answeredEntry,
    caseId,
  });
  await recordAIUsage({ caseId, ownerId, operation: 'incorporateConferenceAnswer', meta: incorporation.meta });

  const updated = await conferenceCaseRepository.update(caseId, {
    conversation,
    currentQuestion: null,
    extractedCase: incorporation.extractedCase,
    promptVersion: { ...caseState.promptVersion, analyze: incorporation.promptVersion },
    aiModel: incorporation.meta.model,
  });

  return applyQuestionOutcome(updated, ownerId);
}

async function finalizeCase({ caseId, ownerId }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.status === ConferenceCaseStatus.COLLECTING_INFORMATION) {
    throw new InvalidStateError('This case still has an open question and is not ready to finalize.');
  }
  if (caseState.status === ConferenceCaseStatus.COMPLETED) {
    throw new InvalidStateError('This case has already been finalized.');
  }

  const result = await conferenceFinalizer.finalizeCase({
    extractedCase: caseState.extractedCase,
    conversation: caseState.conversation,
    originalNarrative: caseState.originalNarrative,
    caseId,
  });
  await recordAIUsage({ caseId, ownerId, operation: 'finalizeConferenceCase', meta: result.meta });

  const report = {
    diagnosis: result.diagnosis,
    indication: result.indication,
    missingInformation: result.missingInformation,
    operativeStrategy: result.operativeStrategy,
    alternatives: result.alternatives,
    controversies: result.controversies,
    technicalConsiderations: result.technicalConsiderations,
    postoperativeConcerns: result.postoperativeConcerns,
    evidenceGuidelines: result.evidenceGuidelines,
  };

  return conferenceCaseRepository.update(caseId, {
    status: ConferenceCaseStatus.COMPLETED,
    report,
    promptVersion: { ...caseState.promptVersion, finalize: result.promptVersion },
    aiModel: result.meta.model,
  });
}

async function getCase({ caseId, ownerId }) {
  return getOwnedCase(caseId, ownerId);
}

/**
 * Lets the trainee hand-edit the finalized report -- e.g. to fix a
 * phrasing the AI got slightly wrong before presenting. Only valid once a
 * case is `completed` (there's nothing to edit before finalizeCase has
 * produced a report).
 */
async function updateReport({ caseId, ownerId, report }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.status !== ConferenceCaseStatus.COMPLETED) {
    throw new InvalidStateError('This case has not been finalized yet.');
  }
  return conferenceCaseRepository.update(caseId, { report: { ...caseState.report, ...report } });
}

/**
 * Returns this case's three heart-team-member responses, generating and
 * caching them on first request (a case that still has an open question
 * has nothing stable to generate responses from yet, so this is rejected
 * until the case reaches ready_to_finalize/completed). Cached on the case
 * itself so repeat views (switching between the three, backing out and
 * returning) never re-run the AI call -- mirrors the referenceLookups
 * caching pattern above.
 */
async function getHeartTeamResponses({ caseId, ownerId }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.heartTeamResponses) {
    return caseState.heartTeamResponses;
  }
  if (caseState.status === ConferenceCaseStatus.COLLECTING_INFORMATION) {
    throw new InvalidStateError('This case still has an open question and is not ready for heart-team responses.');
  }

  const result = await heartTeamResponses.generateResponses({
    extractedCase: caseState.extractedCase,
    conversation: caseState.conversation,
    originalNarrative: caseState.originalNarrative,
    caseId,
  });
  await recordAIUsage({ caseId, ownerId, operation: 'generateHeartTeamResponses', meta: result.meta });

  const responses = {
    surgeon: result.surgeon,
    nonInterventionalCardiologist: result.nonInterventionalCardiologist,
    interventionalCardiologist: result.interventionalCardiologist,
  };
  await conferenceCaseRepository.update(caseId, {
    heartTeamResponses: responses,
    promptVersion: { ...caseState.promptVersion, heartTeamResponses: result.promptVersion },
  });
  return responses;
}

/** Every conference case owned by the caller, most recent first. */
async function listCases({ ownerId }) {
  const cases = await conferenceCaseRepository.listForOwner(ownerId);
  return cases.map((caseState) => ({
    caseId: caseState.objectId,
    title: deriveTitle(caseState.originalNarrative),
    status: caseState.status,
    createdAt: caseState.createdAt,
    updatedAt: caseState.updatedAt,
  }));
}

/**
 * Returns a previously-cached PubMed lookup for a reference topic, or
 * null if this topic hasn't been searched for this case before. Mirrors
 * MMCoach's per-case reference-lookup cache so repeat lookups of the same
 * topic don't repeat an AI call + PubMed round trip.
 */
async function getCachedReferenceLookup({ caseId, ownerId, topic }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  const cached = caseState.referenceLookups && caseState.referenceLookups[topic];
  return { caseState, cached: cached || null };
}

async function cacheReferenceLookup({ caseId, existingLookups, topic, query, results }) {
  const referenceLookups = {
    ...(existingLookups || {}),
    [topic]: { query, results, cachedAt: new Date().toISOString() },
  };
  await conferenceCaseRepository.update(caseId, { referenceLookups });
}

/** Response shape for cscCreateConferenceCase / cscAnswerConferenceQuestion. */
function formatCaseSummary(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    extractedCase: caseState.extractedCase,
    nextQuestion: toNextQuestion(caseState.currentQuestion),
  };
}

/** Response shape for cscFinalizeConferenceCase / cscUpdateConferenceReport. */
function formatFinalizedCase(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    report: caseState.report,
  };
}

/** Response shape for cscGetConferenceCase -- the full client-facing case state. */
function formatFullCase(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    originalNarrative: caseState.originalNarrative,
    extractedCase: caseState.extractedCase,
    conversation: caseState.conversation,
    nextQuestion: toNextQuestion(caseState.currentQuestion),
    report: caseState.report,
    heartTeamResponses: caseState.heartTeamResponses,
    promptVersion: caseState.promptVersion,
    aiModel: caseState.aiModel,
    createdAt: caseState.createdAt,
    updatedAt: caseState.updatedAt,
  };
}

module.exports = {
  createCase,
  answerQuestion,
  finalizeCase,
  getCase,
  updateReport,
  listCases,
  getHeartTeamResponses,
  getCachedReferenceLookup,
  cacheReferenceLookup,
  recordAIUsage,
  formatCaseSummary,
  formatFinalizedCase,
  formatFullCase,
};
