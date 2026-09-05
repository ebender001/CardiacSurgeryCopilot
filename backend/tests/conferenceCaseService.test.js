jest.mock('../cloud/repositories/conferenceCaseRepository');
jest.mock('../cloud/repositories/aiCostRepository');
jest.mock('../cloud/ai/conferenceCaseAnalyzer');
jest.mock('../cloud/ai/conferenceQuestionGenerator');
jest.mock('../cloud/ai/conferenceFinalizer');
jest.mock('../cloud/ai/referenceQueryBuilder');
jest.mock('../cloud/services/pubmedService');

const conferenceCaseRepository = require('../cloud/repositories/conferenceCaseRepository');
const aiCostRepository = require('../cloud/repositories/aiCostRepository');
const conferenceCaseAnalyzer = require('../cloud/ai/conferenceCaseAnalyzer');
const conferenceQuestionGenerator = require('../cloud/ai/conferenceQuestionGenerator');
const conferenceFinalizer = require('../cloud/ai/conferenceFinalizer');
const referenceQueryBuilder = require('../cloud/ai/referenceQueryBuilder');
const pubmedService = require('../cloud/services/pubmedService');
const conferenceCaseService = require('../cloud/services/conferenceCaseService');
const { NotFoundError, InvalidStateError, ValidationError, AIProviderError } = require('../cloud/utils/errors');
const { ConferenceCaseStatus } = require('../cloud/schemas/conferenceCaseStatus');

function baseCaseState(overrides = {}) {
  return {
    objectId: 'case1',
    ownerId: 'user1',
    status: ConferenceCaseStatus.COLLECTING_INFORMATION,
    originalNarrative: 'narrative',
    extractedCase: { diagnosis: 'Severe aortic stenosis' },
    conversation: [],
    currentQuestion: null,
    report: null,
    heartTeamResponses: null,
    heartTeamEvidence: {},
    referenceLookups: {},
    promptVersion: {},
    aiModel: 'gpt-test',
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

afterEach(() => jest.clearAllMocks());

describe('createCase', () => {
  it('asks a follow-up question when the analyzer/question generator says more is needed', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState({ currentQuestion: null });
    conferenceCaseRepository.create.mockResolvedValue(created);
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: true,
      question: { text: 'What is the ejection fraction?', category: 'risk factors', reason: 'Affects risk.' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...created, ...patch }));

    const result = await conferenceCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(result.status).toBe(ConferenceCaseStatus.COLLECTING_INFORMATION);
    expect(result.currentQuestion.question).toBe('What is the ejection fraction?');
  });

  it('transitions straight to ready_to_finalize when no question is needed', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState();
    conferenceCaseRepository.create.mockResolvedValue(created);
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: false,
      question: null,
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...created, ...patch }));

    const result = await conferenceCaseService.createCase({ narrative: 'A fully detailed narrative...', ownerId: 'user1' });

    expect(result.status).toBe(ConferenceCaseStatus.READY_TO_FINALIZE);
    expect(result.currentQuestion).toBeNull();
  });

  it('propagates AI provider failures rather than storing a corrupted case', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockRejectedValue(new AIProviderError('AI provider returned status 500.'));

    await expect(conferenceCaseService.createCase({ narrative: 'narrative', ownerId: 'user1' })).rejects.toThrow(AIProviderError);
    expect(conferenceCaseRepository.create).not.toHaveBeenCalled();
  });
});

describe('answerQuestion', () => {
  it('throws NotFoundError for an unknown case id', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(null);

    await expect(
      conferenceCaseService.answerQuestion({ caseId: 'missing', questionId: 'q1', answer: 'yes', ownerId: 'user1' })
    ).rejects.toThrow(NotFoundError);
  });

  it('throws NotFoundError (not a permission error) when the case belongs to a different user', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ ownerId: 'someone-else' }));

    await expect(
      conferenceCaseService.answerQuestion({ caseId: 'case1', questionId: 'q1', answer: 'yes', ownerId: 'user1' })
    ).rejects.toThrow(NotFoundError);
  });

  it('throws InvalidStateError when the questionId does not match the active question', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(
      baseCaseState({
        currentQuestion: { questionId: 'q1', question: 'Q?', category: 'risk factors', reason: 'r', answer: null, askedAt: 'x', answeredAt: null },
      })
    );

    await expect(
      conferenceCaseService.answerQuestion({ caseId: 'case1', questionId: 'stale-question', answer: 'yes', ownerId: 'user1' })
    ).rejects.toThrow(InvalidStateError);
  });

  it('throws InvalidStateError when the case has no open question', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ status: ConferenceCaseStatus.READY_TO_FINALIZE, currentQuestion: null }));

    await expect(
      conferenceCaseService.answerQuestion({ caseId: 'case1', questionId: 'q1', answer: 'yes', ownerId: 'user1' })
    ).rejects.toThrow(InvalidStateError);
  });

  it('incorporates the answer and asks another question when more is needed', async () => {
    const current = baseCaseState({
      currentQuestion: { questionId: 'q1', question: 'What is the ejection fraction?', category: 'risk factors', reason: 'r', answer: null, askedAt: 'x', answeredAt: null },
    });
    conferenceCaseRepository.getById.mockResolvedValue(current);
    conferenceCaseAnalyzer.incorporateAnswer.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis', ejectionFraction: '55%' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...current, ...patch }));
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: true,
      question: { text: 'Any prior sternotomy?', category: 'prior history', reason: 'r' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });

    const result = await conferenceCaseService.answerQuestion({ caseId: 'case1', questionId: 'q1', answer: '55%', ownerId: 'user1' });

    expect(conferenceCaseAnalyzer.incorporateAnswer).toHaveBeenCalled();
    expect(result.currentQuestion.question).toBe('Any prior sternotomy?');
  });

  it('transitions to ready_to_finalize once nothing more is needed', async () => {
    const current = baseCaseState({
      currentQuestion: { questionId: 'q1', question: 'Q?', category: 'risk factors', reason: 'r', answer: null, askedAt: 'x', answeredAt: null },
    });
    conferenceCaseRepository.getById.mockResolvedValue(current);
    conferenceCaseAnalyzer.incorporateAnswer.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...current, ...patch }));
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: false,
      question: null,
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });

    const result = await conferenceCaseService.answerQuestion({ caseId: 'case1', questionId: 'q1', answer: 'final answer', ownerId: 'user1' });

    expect(result.status).toBe(ConferenceCaseStatus.READY_TO_FINALIZE);
    expect(result.currentQuestion).toBeNull();
  });
});

describe('finalizeCase', () => {
  it('throws InvalidStateError when the case still has an open question', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ status: ConferenceCaseStatus.COLLECTING_INFORMATION }));

    await expect(conferenceCaseService.finalizeCase({ caseId: 'case1', ownerId: 'user1' })).rejects.toThrow(InvalidStateError);
  });

  it('throws InvalidStateError when the case is already completed', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ status: ConferenceCaseStatus.COMPLETED }));

    await expect(conferenceCaseService.finalizeCase({ caseId: 'case1', ownerId: 'user1' })).rejects.toThrow(InvalidStateError);
  });

  it('throws NotFoundError for a nonexistent case', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(null);

    await expect(conferenceCaseService.finalizeCase({ caseId: 'missing', ownerId: 'user1' })).rejects.toThrow(NotFoundError);
  });

  it('throws NotFoundError when the case belongs to a different user', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ status: ConferenceCaseStatus.READY_TO_FINALIZE, ownerId: 'someone-else' }));

    await expect(conferenceCaseService.finalizeCase({ caseId: 'case1', ownerId: 'user1' })).rejects.toThrow(NotFoundError);
  });

  it('generates and persists the finalized nine-section report', async () => {
    const current = baseCaseState({ status: ConferenceCaseStatus.READY_TO_FINALIZE });
    conferenceCaseRepository.getById.mockResolvedValue(current);
    conferenceFinalizer.finalizeCase.mockResolvedValue({
      diagnosis: 'Severe aortic stenosis.',
      indication: 'Symptomatic, meets guideline threshold.',
      missingInformation: 'Coronary anatomy pending.',
      operativeStrategy: 'SAVR planned.',
      alternatives: 'TAVR considered, not preferred.',
      controversies: 'Valve choice given age.',
      technicalConsiderations: 'Calcified annulus.',
      postoperativeConcerns: 'Watch for heart block.',
      evidenceGuidelines: [{ topic: 't', searchIntent: 's', citation: null, verified: false }],
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...current, ...patch }));

    const result = await conferenceCaseService.finalizeCase({ caseId: 'case1', ownerId: 'user1' });

    expect(result.status).toBe(ConferenceCaseStatus.COMPLETED);
    expect(result.report.diagnosis).toBe('Severe aortic stenosis.');
    expect(result.report.evidenceGuidelines).toHaveLength(1);
  });
});

describe('getCase', () => {
  it('throws NotFoundError for an unknown case id', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(null);

    await expect(conferenceCaseService.getCase({ caseId: 'missing', ownerId: 'user1' })).rejects.toThrow(NotFoundError);
  });

  it('throws NotFoundError when the case belongs to a different user', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ ownerId: 'someone-else' }));

    await expect(conferenceCaseService.getCase({ caseId: 'case1', ownerId: 'user1' })).rejects.toThrow(NotFoundError);
  });

  it('returns the case state for a known id owned by the caller', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState());

    const result = await conferenceCaseService.getCase({ caseId: 'case1', ownerId: 'user1' });

    expect(result.objectId).toBe('case1');
  });
});

describe('updateReport', () => {
  it('throws NotFoundError when the case belongs to a different user', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(
      baseCaseState({ status: ConferenceCaseStatus.COMPLETED, ownerId: 'someone-else' })
    );

    await expect(
      conferenceCaseService.updateReport({ caseId: 'case1', ownerId: 'user1', report: { diagnosis: 'Edited.' } })
    ).rejects.toThrow(NotFoundError);
    expect(conferenceCaseRepository.update).not.toHaveBeenCalled();
  });

  it('throws InvalidStateError when the case has not been finalized yet', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ status: ConferenceCaseStatus.READY_TO_FINALIZE }));

    await expect(
      conferenceCaseService.updateReport({ caseId: 'case1', ownerId: 'user1', report: { diagnosis: 'Edited.' } })
    ).rejects.toThrow(InvalidStateError);
    expect(conferenceCaseRepository.update).not.toHaveBeenCalled();
  });

  it('merges the edited fields into the existing report for a completed case', async () => {
    const current = baseCaseState({
      status: ConferenceCaseStatus.COMPLETED,
      report: { diagnosis: 'Original.', indication: 'Original indication.' },
    });
    conferenceCaseRepository.getById.mockResolvedValue(current);
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...current, ...patch }));

    const result = await conferenceCaseService.updateReport({
      caseId: 'case1',
      ownerId: 'user1',
      report: { diagnosis: 'Edited diagnosis.' },
    });

    expect(conferenceCaseRepository.update).toHaveBeenCalledWith('case1', {
      report: { diagnosis: 'Edited diagnosis.', indication: 'Original indication.' },
    });
    expect(result.report.diagnosis).toBe('Edited diagnosis.');
  });
});

describe('AI cost tracking', () => {
  it('records usage for both AI calls made during createCase, tagged with caseType conference', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-4o', usage: { prompt_tokens: 200, completion_tokens: 50, total_tokens: 250 } },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState({ currentQuestion: null });
    conferenceCaseRepository.create.mockResolvedValue(created);
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: false,
      question: null,
      meta: { model: 'gpt-4o', usage: { prompt_tokens: 80, completion_tokens: 20, total_tokens: 100 } },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...created, ...patch }));
    aiCostRepository.record.mockResolvedValue({ costUSD: 0.001, totalTokens: 250 });

    await conferenceCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(aiCostRepository.record).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 'case1', caseType: 'conference', ownerId: 'user1', operation: 'analyzeInitialConferenceNarrative' })
    );
    expect(aiCostRepository.record).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 'case1', caseType: 'conference', ownerId: 'user1', operation: 'generateNextConferenceQuestion' })
    );
    expect(conferenceCaseRepository.incrementAIUsage).toHaveBeenCalledTimes(2);
  });

  it('does not skip the case workflow when a cost-recording failure occurs', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-4o', usage: { prompt_tokens: 200, completion_tokens: 50, total_tokens: 250 } },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState({ currentQuestion: null });
    conferenceCaseRepository.create.mockResolvedValue(created);
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: false,
      question: null,
      meta: { model: 'gpt-4o', usage: { prompt_tokens: 10, completion_tokens: 10, total_tokens: 20 } },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...created, ...patch }));
    aiCostRepository.record.mockRejectedValue(new Error('cost table unavailable'));

    const result = await conferenceCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(result.status).toBe(ConferenceCaseStatus.READY_TO_FINALIZE);
  });

  it('skips recording entirely when the AI call meta has no usage', async () => {
    conferenceCaseAnalyzer.analyzeInitialNarrative.mockResolvedValue({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      meta: { model: 'gpt-4o' },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState({ currentQuestion: null });
    conferenceCaseRepository.create.mockResolvedValue(created);
    conferenceQuestionGenerator.generateNextQuestion.mockResolvedValue({
      needsQuestion: false,
      question: null,
      meta: { model: 'gpt-4o' },
      promptVersion: '1.0.0',
    });
    conferenceCaseRepository.update.mockImplementation(async (id, patch) => ({ ...created, ...patch }));

    await conferenceCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(aiCostRepository.record).not.toHaveBeenCalled();
    expect(conferenceCaseRepository.incrementAIUsage).not.toHaveBeenCalled();
  });
});

describe('reference lookup caching', () => {
  it('getCachedReferenceLookup returns null when the topic has not been searched before', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState());

    const { cached } = await conferenceCaseService.getCachedReferenceLookup({ caseId: 'case1', ownerId: 'user1', topic: 'Valve choice' });

    expect(cached).toBeNull();
  });

  it('getCachedReferenceLookup returns a previously cached lookup for that topic', async () => {
    const cachedLookup = { topic: 'Valve choice', query: 'valve choice[tiab]', results: [{ pmid: '111' }], cachedAt: '2026-01-01T00:00:00.000Z' };
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ referenceLookups: { 'Valve choice': cachedLookup } }));

    const { cached } = await conferenceCaseService.getCachedReferenceLookup({ caseId: 'case1', ownerId: 'user1', topic: 'Valve choice' });

    expect(cached).toEqual(cachedLookup);
  });

  it('cacheReferenceLookup persists the new lookup alongside any existing ones, keeping referenceLookups a plain object', async () => {
    await conferenceCaseService.cacheReferenceLookup({
      caseId: 'case1',
      existingLookups: { 'Other topic': { topic: 'Other topic', query: 'q', results: [], cachedAt: 'x' } },
      topic: 'Valve choice',
      query: 'valve choice[tiab]',
      results: [{ pmid: '111' }],
    });

    expect(conferenceCaseRepository.update).toHaveBeenCalledWith('case1', {
      referenceLookups: expect.objectContaining({
        'Other topic': { topic: 'Other topic', query: 'q', results: [], cachedAt: 'x' },
        'Valve choice': expect.objectContaining({
          topic: 'Valve choice',
          query: 'valve choice[tiab]',
          results: [{ pmid: '111' }],
        }),
      }),
    });
    // referenceLookups is a schema-locked Object column on CSCConferenceCase -- an
    // array would fail the save with a schema-mismatch error in production.
    const [, patch] = conferenceCaseRepository.update.mock.calls[0];
    expect(Array.isArray(patch.referenceLookups)).toBe(false);
  });

  it('sanitizes a "." in the topic so a Mongo/Parse Object-field key stays valid, while preserving the real topic text in the cached entry and on lookup', async () => {
    const topic = 'Comparison of TAVR vs. SAVR in low-risk patients';

    await conferenceCaseService.cacheReferenceLookup({
      caseId: 'case1',
      existingLookups: {},
      topic,
      query: 'tavr vs savr[tiab]',
      results: [{ pmid: '222' }],
    });

    const [, patch] = conferenceCaseRepository.update.mock.calls[0];
    const keys = Object.keys(patch.referenceLookups);
    expect(keys).toHaveLength(1);
    expect(keys[0]).not.toContain('.');
    expect(patch.referenceLookups[keys[0]]).toEqual(expect.objectContaining({ topic }));

    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ referenceLookups: patch.referenceLookups }));
    const { cached } = await conferenceCaseService.getCachedReferenceLookup({ caseId: 'case1', ownerId: 'user1', topic });
    expect(cached).toEqual(expect.objectContaining({ topic, query: 'tavr vs savr[tiab]' }));
  });
});

describe('getRoleEvidence', () => {
  const heartTeamResponses = {
    surgeon: { recommendation: 'Proceed with CABG x3.', rationale: 'Three-vessel disease with reduced EF is a class I surgical indication.' },
  };

  it('rejects an unknown role without calling the repository', async () => {
    await expect(
      conferenceCaseService.getRoleEvidence({ caseId: 'case1', ownerId: 'user1', role: 'nurse' })
    ).rejects.toThrow(ValidationError);
    expect(conferenceCaseRepository.getById).not.toHaveBeenCalled();
  });

  it('throws InvalidStateError when heart team responses have not been generated yet', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ heartTeamResponses: null }));

    await expect(
      conferenceCaseService.getRoleEvidence({ caseId: 'case1', ownerId: 'user1', role: 'surgeon' })
    ).rejects.toThrow(InvalidStateError);
    expect(referenceQueryBuilder.buildQuery).not.toHaveBeenCalled();
  });

  it('returns cached evidence for that role without calling the query builder or PubMed', async () => {
    const cachedEvidence = {
      pro: { query: 'CABG[tiab]', results: [{ pmid: '111' }] },
      con: { query: 'PCI[tiab]', results: [{ pmid: '222' }] },
    };
    conferenceCaseRepository.getById.mockResolvedValue(
      baseCaseState({ heartTeamResponses, heartTeamEvidence: { surgeon: cachedEvidence } })
    );

    const result = await conferenceCaseService.getRoleEvidence({ caseId: 'case1', ownerId: 'user1', role: 'surgeon' });

    expect(result).toEqual(cachedEvidence);
    expect(referenceQueryBuilder.buildQuery).not.toHaveBeenCalled();
    expect(pubmedService.findReferences).not.toHaveBeenCalled();
    expect(conferenceCaseRepository.update).not.toHaveBeenCalled();
  });

  it('builds pro/con queries, searches PubMed within the 10-year window, and caches the result', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ heartTeamResponses }));
    referenceQueryBuilder.buildQuery
      .mockResolvedValueOnce({ query: 'CABG three-vessel[tiab]', meta: { model: 'gpt-test', usage: { prompt_tokens: 10, completion_tokens: 5 } }, promptVersion: '1.0.0' })
      .mockResolvedValueOnce({ query: 'PCI multivessel[tiab]', meta: { model: 'gpt-test', usage: { prompt_tokens: 10, completion_tokens: 5 } }, promptVersion: '1.0.0' });
    pubmedService.findReferences
      .mockResolvedValueOnce([{ pmid: '111', title: 'CABG outcomes' }])
      .mockResolvedValueOnce([{ pmid: '222', title: 'PCI outcomes' }]);

    const result = await conferenceCaseService.getRoleEvidence({ caseId: 'case1', ownerId: 'user1', role: 'surgeon' });

    expect(referenceQueryBuilder.buildQuery).toHaveBeenCalledTimes(2);
    const expectedMinYear = new Date().getFullYear() - 10;
    expect(pubmedService.findReferences).toHaveBeenNthCalledWith(1, { query: 'CABG three-vessel[tiab]', maxResults: 5, minYear: expectedMinYear });
    expect(pubmedService.findReferences).toHaveBeenNthCalledWith(2, { query: 'PCI multivessel[tiab]', maxResults: 5, minYear: expectedMinYear });
    expect(result).toEqual({
      pro: { query: 'CABG three-vessel[tiab]', results: [{ pmid: '111', title: 'CABG outcomes' }] },
      con: { query: 'PCI multivessel[tiab]', results: [{ pmid: '222', title: 'PCI outcomes' }] },
    });
    expect(conferenceCaseRepository.update).toHaveBeenCalledWith('case1', {
      heartTeamEvidence: { surgeon: result },
    });
    expect(aiCostRepository.record).toHaveBeenCalledTimes(2);
  });

  it('asks the query builder for a broader query when the first PubMed search returns nothing', async () => {
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ heartTeamResponses }));
    referenceQueryBuilder.buildQuery
      .mockResolvedValueOnce({ query: 'overly[tiab] AND narrow[tiab] AND query[tiab]', meta: { model: 'gpt-test', usage: { prompt_tokens: 10, completion_tokens: 5 } }, promptVersion: '1.1.0' }) // pro: primary
      .mockResolvedValueOnce({ query: 'PCI multivessel[tiab]', meta: { model: 'gpt-test', usage: { prompt_tokens: 10, completion_tokens: 5 } }, promptVersion: '1.1.0' }) // con: primary, succeeds
      .mockResolvedValueOnce({ query: 'CABG[mesh]', meta: { model: 'gpt-test', usage: { prompt_tokens: 12, completion_tokens: 6 } }, promptVersion: '1.1.0' }); // pro: broadened
    pubmedService.findReferences
      .mockResolvedValueOnce([]) // pro: primary query returns nothing (call 1, started immediately by Promise.all)
      .mockResolvedValueOnce([{ pmid: '222', title: 'PCI outcomes' }]) // con: primary query succeeds, no broadening needed (call 2, started immediately by Promise.all)
      .mockResolvedValueOnce([{ pmid: '999', title: 'CABG broadened result' }]); // pro: broadened query, only awaited after call 1 resolves empty (call 3)

    const result = await conferenceCaseService.getRoleEvidence({ caseId: 'case1', ownerId: 'user1', role: 'surgeon' });

    const expectedMinYear = new Date().getFullYear() - 10;
    expect(referenceQueryBuilder.buildQuery).toHaveBeenCalledTimes(3);
    expect(referenceQueryBuilder.buildQuery).toHaveBeenNthCalledWith(3, {
      topic: 'Evidence supporting this recommendation from a cardiac surgeon: Proceed with CABG x3.',
      searchIntent: expect.stringContaining('returned zero results'),
    });
    expect(pubmedService.findReferences).toHaveBeenCalledTimes(3);
    expect(pubmedService.findReferences).toHaveBeenNthCalledWith(1, { query: 'overly[tiab] AND narrow[tiab] AND query[tiab]', maxResults: 5, minYear: expectedMinYear });
    expect(pubmedService.findReferences).toHaveBeenNthCalledWith(2, { query: 'PCI multivessel[tiab]', maxResults: 5, minYear: expectedMinYear });
    expect(pubmedService.findReferences).toHaveBeenNthCalledWith(3, { query: 'CABG[mesh]', maxResults: 5, minYear: expectedMinYear });
    // The reflected query is whichever one actually produced the results shown.
    expect(result.pro).toEqual({ query: 'CABG[mesh]', results: [{ pmid: '999', title: 'CABG broadened result' }] });
    expect(result.con).toEqual({ query: 'PCI multivessel[tiab]', results: [{ pmid: '222', title: 'PCI outcomes' }] });
    expect(aiCostRepository.record).toHaveBeenCalledTimes(3);
  });
});

describe('listCases', () => {
  it('maps each case to a list row with a derived title', async () => {
    const createdAt = new Date('2026-01-01T00:00:00.000Z');
    const updatedAt = new Date('2026-01-02T00:00:00.000Z');
    conferenceCaseRepository.listForOwner.mockResolvedValue([
      baseCaseState({
        objectId: 'case1',
        originalNarrative: 'A 72-year-old woman with severe symptomatic aortic stenosis being evaluated for SAVR versus TAVR.',
        status: ConferenceCaseStatus.COMPLETED,
        createdAt,
        updatedAt,
      }),
    ]);

    const result = await conferenceCaseService.listCases({ ownerId: 'user1' });

    expect(conferenceCaseRepository.listForOwner).toHaveBeenCalledWith('user1');
    expect(result).toEqual([
      {
        caseId: 'case1',
        title: 'A 72-year-old woman with severe symptomatic aortic stenosis …',
        status: ConferenceCaseStatus.COMPLETED,
        createdAt,
        updatedAt,
      },
    ]);
  });

  it('returns an empty array when the caller owns no cases', async () => {
    conferenceCaseRepository.listForOwner.mockResolvedValue([]);

    expect(await conferenceCaseService.listCases({ ownerId: 'user1' })).toEqual([]);
  });
});

describe('response formatters', () => {
  it('formatCaseSummary maps currentQuestion to the client nextQuestion shape', () => {
    const state = baseCaseState({
      currentQuestion: { questionId: 'q1', question: 'Q text', category: 'risk factors', reason: 'r', answer: null, askedAt: 'x', answeredAt: null },
    });
    expect(conferenceCaseService.formatCaseSummary(state)).toEqual({
      caseId: 'case1',
      status: ConferenceCaseStatus.COLLECTING_INFORMATION,
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      nextQuestion: { id: 'q1', text: 'Q text', category: 'risk factors', reason: 'r' },
    });
  });

  it('formatFinalizedCase excludes internal fields like promptVersion/aiModel', () => {
    const state = baseCaseState({
      status: ConferenceCaseStatus.COMPLETED,
      report: { diagnosis: 'd' },
    });
    const formatted = conferenceCaseService.formatFinalizedCase(state);
    expect(formatted).not.toHaveProperty('promptVersion');
    expect(formatted).not.toHaveProperty('aiModel');
    expect(formatted.caseId).toBe('case1');
  });
});
