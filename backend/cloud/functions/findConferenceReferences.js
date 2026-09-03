const pubmedService = require('../services/pubmedService');
const referenceQueryBuilder = require('../ai/referenceQueryBuilder');
const conferenceCaseService = require('../services/conferenceCaseService');
const { requireNonEmptyString, requireAuthenticatedUser } = require('../utils/validation');
const { toParseError } = require('../utils/errors');
const logger = require('../utils/logger');

function optionalString(value) {
  return typeof value === 'string' && value.trim().length > 0 ? value.trim() : '';
}

/**
 * `caseId` is optional (defensive) but the client always sends it -- when
 * present it's used three ways: to verify the caller owns that case (same
 * NotFoundError either way as every other case-scoped function) before
 * recording anything against it, to serve a previously-looked-up topic
 * straight from that case's cache with no AI or PubMed call at all, and --
 * on a cache miss -- to roll the AI query-building call's cost into that
 * case's running AI-cost total and cache the result for next time.
 */
Parse.Cloud.define('cscFindConferenceReferences', async (request) => {
  const startedAt = Date.now();
  const params = request.params || {};
  try {
    const ownerId = requireAuthenticatedUser(request);
    const topic = requireNonEmptyString(params.topic, 'topic');
    const searchIntent = optionalString(params.searchIntent);
    const caseId = optionalString(params.caseId);

    let existingLookups = null;
    if (caseId) {
      const { caseState, cached } = await conferenceCaseService.getCachedReferenceLookup({ caseId, ownerId, topic });
      if (cached) {
        logger.info({ function: 'cscFindConferenceReferences', caseId, cacheHit: true, resultCount: cached.results.length });
        return { topic, results: cached.results };
      }
      existingLookups = caseState.referenceLookups;
    }

    const queryResult = await referenceQueryBuilder.buildQuery({ topic, searchIntent });
    if (caseId) {
      await conferenceCaseService.recordAIUsage({
        caseId,
        ownerId,
        operation: 'buildReferenceQuery',
        meta: queryResult.meta,
      });
    }

    let results = await pubmedService.findReferences({ query: queryResult.query, maxResults: 5 });
    if (results.length === 0 && queryResult.query !== topic) {
      // The AI-crafted query can occasionally over-constrain (too many
      // ANDed concepts) and return nothing PubMed would otherwise have --
      // fall back to the plain topic rather than showing "no results".
      results = await pubmedService.findReferences({ query: topic, maxResults: 5 });
    }

    if (caseId) {
      await conferenceCaseService.cacheReferenceLookup({ caseId, existingLookups, topic, query: queryResult.query, results });
    }

    logger.info({
      function: 'cscFindConferenceReferences',
      resultCount: results.length,
      latencyMs: Date.now() - startedAt,
    });

    return { topic, results };
  } catch (err) {
    logger.error({ function: 'cscFindConferenceReferences', message: err.message });
    throw toParseError(err);
  }
}, { requireUser: true });
