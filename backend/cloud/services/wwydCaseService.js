/**
 * Coordinates the What Would You Do workflow: building a case opening from
 * a trainee's raw narrative, then holding the live discussion turn by
 * turn. This is the module Cloud Functions call into -- it delegates the
 * AI calls to ai/*, and persistence to wwydCaseRepository. Structurally
 * simpler than conferenceCaseService.js: no question loop, no finalize
 * step, just build-once then converse-per-turn.
 */
const wwydCaseRepository = require('../repositories/wwydCaseRepository');
const aiCostRepository = require('../repositories/aiCostRepository');
const wwydCaseBuilder = require('../ai/wwydCaseBuilder');
const wwydConversation = require('../ai/wwydConversation');
const { WwydCaseStatus } = require('../schemas/wwydCaseStatus');
const { NotFoundError } = require('../utils/errors');
const { generateId } = require('../utils/idGenerator');
const { deriveTitle } = require('../utils/caseTitle');
const logger = require('../utils/logger');

/**
 * Records one AI provider call's cost/token usage against a case and
 * rolls it into CSCWwydCase's running total. A no-op if `meta.usage` is
 * missing (the provider didn't return one) -- callers still get their
 * case update either way, cost tracking is never allowed to block or
 * fail the actual workflow it's observing.
 */
async function recordAIUsage({ caseId, ownerId, operation, meta }) {
  if (!meta || !meta.usage) return;
  try {
    const { costUSD, totalTokens } = await aiCostRepository.record({
      caseId,
      caseType: 'wwyd',
      ownerId,
      operation,
      model: meta.model,
      usage: meta.usage,
      latencyMs: meta.latencyMs,
    });
    await wwydCaseRepository.incrementAIUsage(caseId, { costUSD, totalTokens });
  } catch (err) {
    logger.error({ module: 'wwydCaseService', operation: 'recordAIUsage', caseId, message: err.message });
  }
}

/**
 * Fetches a case and confirms `ownerId` owns it, in one step. A case that
 * exists but belongs to someone else is rejected the same way as one that
 * doesn't exist at all -- a case id alone must never reveal whether it
 * belongs to another user, let alone grant access to it.
 */
async function getOwnedCase(caseId, ownerId) {
  const caseState = await wwydCaseRepository.getById(caseId);
  if (!caseState || caseState.ownerId !== ownerId) {
    throw new NotFoundError(`No WWYD case found with id ${caseId}.`);
  }
  return caseState;
}

async function createCase({ narrative, ownerId }) {
  const built = await wwydCaseBuilder.buildCase({ narrative, caseId: 'new' });

  const created = await wwydCaseRepository.create({
    ownerId,
    status: WwydCaseStatus.ACTIVE,
    originalNarrative: narrative,
    casePresentation: built.casePresentation,
    startingQuestion: built.startingQuestion,
    conversation: [],
    promptVersion: { build: built.promptVersion },
    aiModel: built.meta.model,
  });

  await recordAIUsage({ caseId: created.objectId, ownerId, operation: 'buildWwydCase', meta: built.meta });

  const saved = await wwydCaseRepository.getById(created.objectId);
  logger.info({ module: 'wwydCaseService', caseId: saved.objectId, operation: 'createCase', status: saved.status });
  return saved;
}

async function sendMessage({ caseId, ownerId, message }) {
  const caseState = await getOwnedCase(caseId, ownerId);

  const result = await wwydConversation.continueConversation({
    casePresentation: caseState.casePresentation,
    startingQuestion: caseState.startingQuestion,
    conversation: caseState.conversation,
    newMessage: message,
    caseId,
  });

  const now = new Date().toISOString();
  const conversation = [
    ...caseState.conversation,
    { id: generateId('msg'), role: 'user', text: message, createdAt: now },
    { id: generateId('msg'), role: 'assistant', text: result.reply, createdAt: now },
  ];

  await wwydCaseRepository.update(caseId, {
    conversation,
    promptVersion: { ...caseState.promptVersion, conversation: result.promptVersion },
    aiModel: result.meta.model,
  });

  await recordAIUsage({ caseId, ownerId, operation: 'continueWwydConversation', meta: result.meta });

  const saved = await wwydCaseRepository.getById(caseId);
  logger.info({ module: 'wwydCaseService', caseId, operation: 'sendMessage', messageCount: saved.conversation.length });
  return saved;
}

async function getCase({ caseId, ownerId }) {
  return getOwnedCase(caseId, ownerId);
}

/** Every WWYD case owned by the caller, most recent first -- the recent-discussions list. */
async function listCases({ ownerId }) {
  const cases = await wwydCaseRepository.listForOwner(ownerId);
  return cases.map((caseState) => ({
    caseId: caseState.objectId,
    title: deriveTitle(caseState.originalNarrative),
    status: caseState.status,
    createdAt: caseState.createdAt,
    updatedAt: caseState.updatedAt,
  }));
}

/** Response shape for cscCreateWwydCase / cscSendWwydMessage / cscGetWwydCase. */
function formatCase(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    casePresentation: caseState.casePresentation,
    startingQuestion: caseState.startingQuestion,
    conversation: caseState.conversation,
  };
}

/** Full client-facing case state, including fields formatCase omits. */
function formatFullCase(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    originalNarrative: caseState.originalNarrative,
    casePresentation: caseState.casePresentation,
    startingQuestion: caseState.startingQuestion,
    conversation: caseState.conversation,
    promptVersion: caseState.promptVersion,
    aiModel: caseState.aiModel,
    createdAt: caseState.createdAt,
    updatedAt: caseState.updatedAt,
  };
}

module.exports = {
  createCase,
  sendMessage,
  getCase,
  listCases,
  formatCase,
  formatFullCase,
};
