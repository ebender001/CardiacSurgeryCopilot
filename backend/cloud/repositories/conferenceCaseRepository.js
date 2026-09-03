/**
 * The only module that touches Parse.Object / Parse.Query directly for
 * CSCConferenceCase. Everything else works with plain JSON case objects.
 *
 * Uses the master key because Cloud Code is the only path allowed to
 * read/write CSCConferenceCase -- Class-Level Permissions on it deny direct
 * client REST/SDK access. Master key use means Parse's own ACL enforcement
 * never runs here, so per-user ownership is enforced explicitly in
 * `services/conferenceCaseService.js` (it compares `request.user.id`
 * against the `ownerId` this module returns) rather than relied upon
 * implicitly. The ACL this module still sets on every case is
 * defense-in-depth for if CLP is ever loosened later, not the primary
 * access-control mechanism.
 */
const CASE_CLASS_NAME = 'CSCConferenceCase';

function getConferenceCaseClass() {
  return Parse.Object.extend(CASE_CLASS_NAME);
}

function applyFields(parseObject, fields) {
  Object.entries(fields).forEach(([key, value]) => {
    parseObject.set(key, value === undefined ? null : value);
  });
}

function toClientJSON(parseObject) {
  const owner = parseObject.get('owner');
  return {
    objectId: parseObject.id,
    ownerId: owner ? owner.id : null,
    status: parseObject.get('status'),
    originalNarrative: parseObject.get('originalNarrative') || '',
    extractedCase: parseObject.get('extractedCase') || {},
    conversation: parseObject.get('conversation') || [],
    currentQuestion: parseObject.get('currentQuestion') || null,
    report: parseObject.get('report') || null,
    heartTeamResponses: parseObject.get('heartTeamResponses') || null,
    promptVersion: parseObject.get('promptVersion') || {},
    aiModel: parseObject.get('aiModel') || null,
    aiCostUSD: parseObject.get('aiCostUSD') || 0,
    aiTotalTokens: parseObject.get('aiTotalTokens') || 0,
    referenceLookups: parseObject.get('referenceLookups') || {},
    createdAt: parseObject.createdAt,
    updatedAt: parseObject.updatedAt,
  };
}

/**
 * `record` must include `ownerId` -- the authenticated caller's user id,
 * resolved by the Cloud Function from `request.user` (see
 * `utils/validation.js#requireAuthenticatedUser`). Never accept an
 * `ownerId` sourced from client params.
 */
async function create({ ownerId, ...record }) {
  const ConferenceCase = getConferenceCaseClass();
  const parseObject = new ConferenceCase();
  applyFields(parseObject, { aiCostUSD: 0, aiTotalTokens: 0, ...record });

  const owner = Parse.User.createWithoutData(ownerId);
  parseObject.set('owner', owner);

  const acl = new Parse.ACL();
  acl.setPublicReadAccess(false);
  acl.setPublicWriteAccess(false);
  acl.setReadAccess(ownerId, true);
  acl.setWriteAccess(ownerId, true);
  parseObject.setACL(acl);

  await parseObject.save(null, { useMasterKey: true });
  return toClientJSON(parseObject);
}

/**
 * Returns the client-facing case JSON, or null if the id doesn't resolve
 * to an existing CSCConferenceCase (including malformed ids) -- callers
 * treat both the same way: case not found.
 */
async function getById(caseId) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  try {
    const parseObject = await query.get(caseId, { useMasterKey: true });
    return toClientJSON(parseObject);
  } catch (err) {
    return null;
  }
}

async function update(caseId, patch) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  const parseObject = await query.get(caseId, { useMasterKey: true });
  applyFields(parseObject, patch);
  await parseObject.save(null, { useMasterKey: true });
  return toClientJSON(parseObject);
}

/**
 * Adds to the case's running AI-cost/token totals -- called once per AI
 * provider call (see conferenceCaseService.js), separately from
 * `update()`'s patch-based writes since this needs a true atomic
 * increment rather than a read-then-overwrite of a JS number, which would
 * lose an update if two AI calls for the same case ever finished close
 * together. `costUSD` may be `null` (unpriced model, see
 * `config/aiPricing.js`), in which case only the token total advances.
 */
async function incrementAIUsage(caseId, { costUSD, totalTokens }) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  const parseObject = await query.get(caseId, { useMasterKey: true });
  if (typeof costUSD === 'number') {
    parseObject.increment('aiCostUSD', costUSD);
  }
  parseObject.increment('aiTotalTokens', totalTokens || 0);
  await parseObject.save(null, { useMasterKey: true });
}

/**
 * Every case owned by a user, most recent first -- the single source of
 * truth for the client's "Recent Cases" list on the Conference tab.
 * Returns full case JSON (same shape as getById); callers that only need
 * list-row fields pick those out themselves rather than this trimming the
 * shape, so there's one case JSON shape everywhere.
 */
async function listForOwner(ownerId) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  query.equalTo('owner', Parse.User.createWithoutData(ownerId));
  query.descending('createdAt');
  query.limit(200);
  const cases = await query.find({ useMasterKey: true });
  return cases.map(toClientJSON);
}

/** Number of conference cases owned by a user, regardless of status. */
async function countByOwner(ownerId) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  query.equalTo('owner', Parse.User.createWithoutData(ownerId));
  return query.count({ useMasterKey: true });
}

/**
 * Permanently removes every conference case a user owns -- part of
 * account deletion (see services/accountService.js). Does not touch
 * CSCCaseAICost rows; the caller is responsible for also clearing those
 * via aiCostRepository.deleteAllForOwner, since this module never
 * touches that class.
 */
async function deleteAllForOwner(ownerId) {
  const ConferenceCase = getConferenceCaseClass();
  const query = new Parse.Query(ConferenceCase);
  query.equalTo('owner', Parse.User.createWithoutData(ownerId));
  query.limit(1000);
  const cases = await query.find({ useMasterKey: true });
  if (cases.length > 0) {
    await Parse.Object.destroyAll(cases, { useMasterKey: true });
  }
}

module.exports = {
  create,
  getById,
  update,
  incrementAIUsage,
  listForOwner,
  countByOwner,
  deleteAllForOwner,
  toClientJSON,
  CASE_CLASS_NAME,
};
