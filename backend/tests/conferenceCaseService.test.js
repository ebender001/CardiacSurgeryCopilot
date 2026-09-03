jest.mock('../cloud/repositories/conferenceCaseRepository');
jest.mock('../cloud/repositories/aiCostRepository');
jest.mock('../cloud/ai/conferenceCaseAnalyzer');
jest.mock('../cloud/ai/conferenceQuestionGenerator');
jest.mock('../cloud/ai/conferenceFinalizer');

const conferenceCaseRepository = require('../cloud/repositories/conferenceCaseRepository');
const aiCostRepository = require('../cloud/repositories/aiCostRepository');
const conferenceCaseAnalyzer = require('../cloud/ai/conferenceCaseAnalyzer');
const conferenceQuestionGenerator = require('../cloud/ai/conferenceQuestionGenerator');
const conferenceFinalizer = require('../cloud/ai/conferenceFinalizer');
const conferenceCaseService = require('../cloud/services/conferenceCaseService');
const { NotFoundError, InvalidStateError, AIProviderError } = require('../cloud/utils/errors');
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
    const cachedLookup = { query: 'valve choice[tiab]', results: [{ pmid: '111' }], cachedAt: '2026-01-01T00:00:00.000Z' };
    conferenceCaseRepository.getById.mockResolvedValue(baseCaseState({ referenceLookups: { 'Valve choice': cachedLookup } }));

    const { cached } = await conferenceCaseService.getCachedReferenceLookup({ caseId: 'case1', ownerId: 'user1', topic: 'Valve choice' });

    expect(cached).toEqual(cachedLookup);
  });

  it('cacheReferenceLookup persists the new lookup alongside any existing ones', async () => {
    await conferenceCaseService.cacheReferenceLookup({
      caseId: 'case1',
      existingLookups: { 'Other topic': { query: 'q', results: [], cachedAt: 'x' } },
      topic: 'Valve choice',
      query: 'valve choice[tiab]',
      results: [{ pmid: '111' }],
    });

    expect(conferenceCaseRepository.update).toHaveBeenCalledWith('case1', {
      referenceLookups: expect.objectContaining({
        'Other topic': { query: 'q', results: [], cachedAt: 'x' },
        'Valve choice': expect.objectContaining({
          query: 'valve choice[tiab]',
          results: [{ pmid: '111' }],
        }),
      }),
    });
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
