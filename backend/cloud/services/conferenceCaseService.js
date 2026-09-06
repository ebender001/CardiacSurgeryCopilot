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
const referenceQueryBuilder = require('../ai/referenceQueryBuilder');
const pubmedService = require('./pubmedService');
const { ConferenceCaseStatus } = require('../schemas/conferenceCaseStatus');
const { ROLES: HEART_TEAM_ROLES } = require('../schemas/heartTeamResponseSchema');
const { NotFoundError, InvalidStateError, ValidationError } = require('../utils/errors');
const { generateId } = require('../utils/idGenerator');
const { deriveTitle } = require('../utils/caseTitle');
const logger = require('../utils/logger');

const CASE_TYPE = 'conference';
const EVIDENCE_MAX_RESULTS = 5;
const EVIDENCE_MAX_AGE_YEARS = 10;

/**
 * Durability/reintervention language in a role's recommendation or
 * rationale (see persona.js's guidance on flagging follow-up-duration
 * mismatches, e.g. TAVR vs. SAVR) signals that the evidence which
 * actually matters here is long-term valve/structural durability data --
 * which is frequently older than `EVIDENCE_MAX_AGE_YEARS` precisely
 * because it takes that long to accumulate. See `getRoleEvidence`, which
 * drops the recency floor entirely for a role whose opinion turns on
 * this, rather than either applying that floor everywhere (most evidence
 * searches genuinely do benefit from staying recent) or nowhere
 * (durability studies would otherwise be systematically excluded).
 */
const DURABILITY_EVIDENCE_KEYWORDS = [
  'durability', 'durable', 'structural valve deterioration', 'svd',
  'reintervention', 're-intervention', 'redo surgery', 'redo operation',
  'valve-in-valve', 'valve in valve', 'explant', 'long-term', 'longevity',
  'lifetime', 'life expectancy', 'year follow-up', 'years of follow-up',
];

function needsDurabilityEvidence(text) {
  const lower = text.toLowerCase();
  return DURABILITY_EVIDENCE_KEYWORDS.some((keyword) => lower.includes(keyword));
}

/**
 * Plain-language role labels used only to phrase the pro/con PubMed-
 * query-builder prompts in `getRoleEvidence` below -- not shown to the
 * client (the client already has its own display names, see
 * HeartTeamRole.displayName on iOS).
 */
const EVIDENCE_ROLE_LABELS = {
  surgeon: 'a cardiac surgeon',
  nonInterventionalCardiologist: 'a non-interventional cardiologist',
  interventionalCardiologist: 'an interventional cardiologist',
};

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

/**
 * Lets the trainee deliberately stop the follow-up-question loop early
 * rather than answer every question the AI would otherwise ask --
 * transitions straight to `ready_to_finalize` without another
 * generateNextQuestion call, overriding the AI's own judgment about
 * whether enough is known yet. No AI call is needed for this action
 * itself (it's a pure state transition), unlike answerQuestion.
 *
 * The currently-pending question is recorded in the conversation as
 * explicitly declined -- with its own category/reason intact -- rather
 * than simply discarded, so every downstream AI call (finalizeCase,
 * heart-team responses) sees exactly what was left unknown and that it
 * was a deliberate trainee choice, not an oversight, the same way it
 * already sees every answered question (see qaSummary formatting in
 * finalizeConferencePrompt.js / heartTeamResponsesPrompt.js) -- so
 * "missingInformation" and each role's reasoning can account for it
 * without needing a separate signal threaded through every prompt.
 */
async function skipRemainingQuestions({ caseId, ownerId }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.status !== ConferenceCaseStatus.COLLECTING_INFORMATION || !caseState.currentQuestion) {
    throw new InvalidStateError('This case is not currently awaiting an answer.');
  }

  const declinedEntry = {
    ...caseState.currentQuestion,
    answer: 'The trainee chose to stop answering follow-up questions at this point and proceed with the information gathered so far.',
    answeredAt: new Date().toISOString(),
  };
  const conversation = [...caseState.conversation, declinedEntry];

  const updated = await conferenceCaseRepository.update(caseId, {
    conversation,
    currentQuestion: null,
    status: ConferenceCaseStatus.READY_TO_FINALIZE,
  });

  logger.info({
    module: 'conferenceCaseService',
    caseId: updated.objectId,
    operation: 'skipRemainingQuestions',
    status: updated.status,
  });
  return updated;
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

/**
 * Runs a PubMed search for the AI-crafted `primaryQuery`, and if it comes
 * back empty, asks the query builder for a second, explicitly broader
 * attempt rather than showing "no results" -- the AI query-builder
 * sometimes ANDs in an incidental patient-specific detail (an exact lab
 * value, a specific circumstance) as its own mandatory concept, which can
 * make an otherwise well-formed query return nothing despite the topic
 * itself being well covered in the literature. This deliberately does NOT
 * fall back to sending `topic` to PubMed as bare free text the way
 * cscFindConferenceReferences does for its (short, already search-friendly)
 * discussion topics -- `topic`/`searchIntent` here are full descriptive
 * sentences, and PubMed's automatic term mapping ANDs together every
 * recognized word in an unquoted query, so a sentence full of generic
 * framing language ("Evidence supporting this recommendation from...")
 * reliably returns zero regardless of the actual clinical content.
 * `minYear` (which may already be `undefined` -- see `getRoleEvidence`'s
 * durability handling) is applied identically to both attempts; broadening
 * never itself changes the recency floor.
 */
async function findEvidenceWithFallback({ topic, searchIntent, primaryQuery, maxResults, minYear, caseId, ownerId, operation }) {
  const results = await pubmedService.findReferences({ query: primaryQuery, maxResults, minYear });
  if (results.length > 0) {
    return { query: primaryQuery, results };
  }

  const resultWindowDescription = minYear
    ? `in the last ${new Date().getFullYear() - minYear} years`
    : 'in the available literature';
  const broadened = await referenceQueryBuilder.buildQuery({
    topic,
    searchIntent: `${searchIntent}\n\nThe query "${primaryQuery}" returned zero results ${resultWindowDescription}. Write a broader query this time -- at most two ANDed concepts: the core intervention/condition, plus a comparator only if essential. Drop every incidental patient-specific detail (an exact lab value, a specific percentage, a demographic or social circumstance) entirely rather than including it as a concept, even if that means the query is more general than the topic above.`,
  });
  await recordAIUsage({ caseId, ownerId, operation, meta: broadened.meta });

  const broadenedResults = await pubmedService.findReferences({ query: broadened.query, maxResults, minYear });
  return { query: broadened.query, results: broadenedResults };
}

/**
 * Runs one pro/con PubMed evidence search for a single heart-team role's
 * stated opinion on this case -- "pro" evidence supports that role's
 * `recommendation`, "con" evidence favors an alternative or argues
 * against it. Requires `getHeartTeamResponses` to have already run for
 * this case (there's no opinion to find evidence for before that).
 * Cached per-role on the case, mirroring `getHeartTeamResponses`, so
 * revisiting a role's evidence never repeats the AI query-building calls
 * or the PubMed round trips. Results are restricted to publications from
 * the last `EVIDENCE_MAX_AGE_YEARS` years (see pubmedService's `minYear`)
 * -- never fabricated if fewer than `EVIDENCE_MAX_RESULTS` turn up within
 * that window -- UNLESS this role's recommendation/rationale turns on
 * durability or reintervention (see `needsDurabilityEvidence`), in which
 * case the recency floor is dropped entirely so long-term valve-durability
 * literature older than that window isn't excluded from exactly the
 * search where it matters most.
 */
async function getRoleEvidence({ caseId, ownerId, role }) {
  if (!HEART_TEAM_ROLES.includes(role)) {
    throw new ValidationError(`role must be one of: ${HEART_TEAM_ROLES.join(', ')}.`);
  }

  const caseState = await getOwnedCase(caseId, ownerId);
  if (caseState.heartTeamEvidence && caseState.heartTeamEvidence[role]) {
    return caseState.heartTeamEvidence[role];
  }
  if (!caseState.heartTeamResponses || !caseState.heartTeamResponses[role]) {
    throw new InvalidStateError("Generate this case's heart team responses before requesting evidence.");
  }

  const { recommendation, rationale } = caseState.heartTeamResponses[role];
  const roleLabel = EVIDENCE_ROLE_LABELS[role];
  const minYear = needsDurabilityEvidence(`${recommendation}\n${rationale}`)
    ? undefined
    : new Date().getFullYear() - EVIDENCE_MAX_AGE_YEARS;
  const proTopic = `Evidence supporting this recommendation from ${roleLabel}: ${recommendation}`;
  const conTopic = `Evidence favoring an alternative to this recommendation from ${roleLabel}: ${recommendation}`;

  const [proQuery, conQuery] = await Promise.all([
    referenceQueryBuilder.buildQuery({
      topic: proTopic,
      searchIntent: `High-quality evidence (trials, guidelines, comparative studies) that supports this reasoning: ${rationale}`,
    }),
    referenceQueryBuilder.buildQuery({
      topic: conTopic,
      searchIntent: `High-quality evidence that argues against this recommendation, or favors a different approach, given this reasoning: ${rationale}`,
    }),
  ]);
  await recordAIUsage({ caseId, ownerId, operation: 'buildHeartTeamProEvidenceQuery', meta: proQuery.meta });
  await recordAIUsage({ caseId, ownerId, operation: 'buildHeartTeamConEvidenceQuery', meta: conQuery.meta });

  const [proOutcome, conOutcome] = await Promise.all([
    findEvidenceWithFallback({
      topic: proTopic,
      searchIntent: `High-quality evidence (trials, guidelines, comparative studies) that supports this reasoning: ${rationale}`,
      primaryQuery: proQuery.query,
      maxResults: EVIDENCE_MAX_RESULTS,
      minYear,
      caseId,
      ownerId,
      operation: 'buildHeartTeamProEvidenceQueryBroadened',
    }),
    findEvidenceWithFallback({
      topic: conTopic,
      searchIntent: `High-quality evidence that argues against this recommendation, or favors a different approach, given this reasoning: ${rationale}`,
      primaryQuery: conQuery.query,
      maxResults: EVIDENCE_MAX_RESULTS,
      minYear,
      caseId,
      ownerId,
      operation: 'buildHeartTeamConEvidenceQueryBroadened',
    }),
  ]);
  logger.info({
    module: 'conferenceCaseService',
    operation: 'getRoleEvidence',
    caseId,
    role,
    proQuery: proOutcome.query,
    conQuery: conOutcome.query,
    proCount: proOutcome.results.length,
    conCount: conOutcome.results.length,
  });

  const evidence = {
    pro: { query: proOutcome.query, results: proOutcome.results },
    con: { query: conOutcome.query, results: conOutcome.results },
  };
  await conferenceCaseRepository.update(caseId, {
    heartTeamEvidence: { ...(caseState.heartTeamEvidence || {}), [role]: evidence },
  });
  return evidence;
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
 * `referenceLookups` is schema-locked on CSCConferenceCase as an Object
 * column (set by its very first save), so it must stay a plain object
 * keyed by topic rather than an array -- Parse/Mongo enforce that column
 * type per-field and reject a mismatched shape outright ("schema mismatch
 * ... expected Object but got Array").
 *
 * The raw topic text can't be used as that key directly, though: a nested
 * Mongo subdocument key can't contain "." (e.g. "TAVR vs. SAVR"), and the
 * save fails with an opaque error that surfaced to the client as a
 * generic "Couldn't search PubMed" failure even though PubMed itself had
 * already returned results successfully. Swap "." and "$" for lookalike
 * characters that are safe as object keys -- the real topic text is kept
 * inside the cached entry, so nothing about the cache's meaning changes,
 * only what's usable as its key.
 */
function referenceLookupKey(topic) {
  return topic.replace(/\./g, '․').replace(/\$/g, '＄');
}

/**
 * Returns a previously-cached PubMed lookup for a reference topic, or
 * null if this topic hasn't been searched for this case before. Mirrors
 * MMCoach's per-case reference-lookup cache so repeat lookups of the same
 * topic don't repeat an AI call + PubMed round trip.
 */
async function getCachedReferenceLookup({ caseId, ownerId, topic }) {
  const caseState = await getOwnedCase(caseId, ownerId);
  const cached = caseState.referenceLookups && caseState.referenceLookups[referenceLookupKey(topic)];
  return { caseState, cached: cached || null };
}

async function cacheReferenceLookup({ caseId, existingLookups, topic, query, results }) {
  const referenceLookups = {
    ...(existingLookups || {}),
    [referenceLookupKey(topic)]: { topic, query, results, cachedAt: new Date().toISOString() },
  };
  await conferenceCaseRepository.update(caseId, { referenceLookups });
}

/**
 * Response shape for cscCreateConferenceCase / cscAnswerConferenceQuestion
 * / cscSkipRemainingConferenceQuestions. Includes `originalNarrative` (cheap --
 * already in memory on `caseState`) so the client can show the trainee what
 * they typed/dictated while answering a follow-up question, without a
 * separate cscGetConferenceCase round trip.
 */
function formatCaseSummary(caseState) {
  return {
    caseId: caseState.objectId,
    status: caseState.status,
    originalNarrative: caseState.originalNarrative,
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
  skipRemainingQuestions,
  finalizeCase,
  getCase,
  updateReport,
  listCases,
  getHeartTeamResponses,
  getRoleEvidence,
  getCachedReferenceLookup,
  cacheReferenceLookup,
  recordAIUsage,
  formatCaseSummary,
  formatFinalizedCase,
  formatFullCase,
};
