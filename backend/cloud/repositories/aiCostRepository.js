/**
 * The only module that touches Parse.Object/Parse.Query directly for
 * CSCCaseAICost -- one row per AI provider call, so cost is auditable
 * per-operation rather than only visible as a running total. Feeds each
 * case class's own `incrementAIUsage`-style running-total field (see
 * conferenceCaseRepository.js / wwydCaseRepository.js) -- this module
 * never touches either case class directly.
 *
 * Unlike MMCoach's single-case-class aiCostRepository, this app has two
 * case classes (CSCConferenceCase, CSCWwydCase), so a row is tagged by a
 * plain `caseId` string + `caseType` ('conference' | 'wwyd') instead of a
 * strict Parse Pointer to one specific class -- a Pointer field can't
 * cleanly point at either of two different classes across rows.
 *
 * Uses the master key for the same reason the case repositories do: only
 * Cloud Code should read/write this class. Per-object ACL below is
 * defense-in-depth, not the primary access control -- the real ownership
 * check happens in the service layer that calls this.
 */
const { estimateCostUSD } = require('../config/aiPricing');

const AI_COST_CLASS_NAME = 'CSCCaseAICost';

function getAICostClass() {
  return Parse.Object.extend(AI_COST_CLASS_NAME);
}

function toClientJSON(parseObject) {
  return {
    objectId: parseObject.id,
    caseId: parseObject.get('caseId'),
    caseType: parseObject.get('caseType'),
    operation: parseObject.get('operation'),
    model: parseObject.get('model'),
    promptTokens: parseObject.get('promptTokens') || 0,
    completionTokens: parseObject.get('completionTokens') || 0,
    totalTokens: parseObject.get('totalTokens') || 0,
    costUSD: parseObject.get('costUSD'),
    latencyMs: parseObject.get('latencyMs') || null,
    createdAt: parseObject.createdAt,
  };
}

/**
 * Records one AI provider call against a case. `usage` is the raw OpenAI
 * `usage` object from `aiService.completeJSON`'s `meta` (may be null if
 * the provider didn't return one). Returns the computed `costUSD`
 * (`null` if `model` isn't in the pricing table) and `totalTokens` so the
 * caller can roll them into that case's running total.
 */
async function record({ caseId, caseType, ownerId, operation, model, usage, latencyMs }) {
  const AICost = getAICostClass();
  const row = new AICost();

  const promptTokens = (usage && usage.prompt_tokens) || 0;
  const completionTokens = (usage && usage.completion_tokens) || 0;
  const totalTokens = (usage && usage.total_tokens) || promptTokens + completionTokens;
  const costUSD = estimateCostUSD(model, usage);

  row.set('caseId', caseId);
  row.set('caseType', caseType);
  row.set('owner', Parse.User.createWithoutData(ownerId));
  row.set('operation', operation);
  row.set('model', model);
  row.set('promptTokens', promptTokens);
  row.set('completionTokens', completionTokens);
  row.set('totalTokens', totalTokens);
  row.set('costUSD', costUSD);
  row.set('latencyMs', latencyMs || null);

  const acl = new Parse.ACL();
  acl.setPublicReadAccess(false);
  acl.setPublicWriteAccess(false);
  acl.setReadAccess(ownerId, true);
  acl.setWriteAccess(ownerId, true);
  row.setACL(acl);

  await row.save(null, { useMasterKey: true });
  return { costUSD, totalTokens };
}

/** Every recorded AI-call row for a case, most recent first. */
async function listForCase(caseId) {
  const AICost = getAICostClass();
  const query = new Parse.Query(AICost);
  query.equalTo('caseId', caseId);
  query.descending('createdAt');
  const rows = await query.find({ useMasterKey: true });
  return rows.map(toClientJSON);
}

/**
 * Permanently removes every AI-cost row for a user -- part of account
 * deletion (see functions/deleteAccount.js). Queried by `owner` directly
 * rather than by first listing the user's case ids, since every row
 * already carries its own owner pointer (see `record()` above).
 */
async function deleteAllForOwner(ownerId) {
  const AICost = getAICostClass();
  const query = new Parse.Query(AICost);
  query.equalTo('owner', Parse.User.createWithoutData(ownerId));
  query.limit(1000);
  const rows = await query.find({ useMasterKey: true });
  if (rows.length > 0) {
    await Parse.Object.destroyAll(rows, { useMasterKey: true });
  }
}

/**
 * Every AI-cost row across every user, oldest first -- admin export only.
 * Paginates past Parse's 1000-row query cap with skip/limit rather than
 * `query.each`, since callers want a stable ascending-by-createdAt order
 * for reporting.
 */
async function listAll({ sinceDate } = {}) {
  const AICost = getAICostClass();
  const pageSize = 1000;
  const rows = [];
  let skip = 0;

  for (;;) {
    const query = new Parse.Query(AICost);
    if (sinceDate) {
      query.greaterThanOrEqualTo('createdAt', sinceDate);
    }
    query.ascending('createdAt');
    query.limit(pageSize);
    query.skip(skip);
    // eslint-disable-next-line no-await-in-loop
    const page = await query.find({ useMasterKey: true });

    for (const row of page) {
      const ownerObj = row.get('owner');
      rows.push({
        objectId: row.id,
        caseId: row.get('caseId') || null,
        caseType: row.get('caseType') || null,
        ownerId: ownerObj ? ownerObj.id : null,
        operation: row.get('operation'),
        model: row.get('model'),
        promptTokens: row.get('promptTokens') || 0,
        completionTokens: row.get('completionTokens') || 0,
        totalTokens: row.get('totalTokens') || 0,
        costUSD: row.get('costUSD'),
        latencyMs: row.get('latencyMs') || null,
        createdAt: row.createdAt,
      });
    }

    if (page.length < pageSize) break;
    skip += pageSize;
  }

  return rows;
}

module.exports = {
  record,
  listForCase,
  listAll,
  deleteAllForOwner,
  CLASS_NAME: AI_COST_CLASS_NAME,
};
