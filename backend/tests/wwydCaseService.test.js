jest.mock('../cloud/repositories/wwydCaseRepository');
jest.mock('../cloud/repositories/aiCostRepository');
jest.mock('../cloud/ai/wwydCaseBuilder');
jest.mock('../cloud/ai/wwydConversation');

const wwydCaseRepository = require('../cloud/repositories/wwydCaseRepository');
const aiCostRepository = require('../cloud/repositories/aiCostRepository');
const wwydCaseBuilder = require('../cloud/ai/wwydCaseBuilder');
const wwydConversation = require('../cloud/ai/wwydConversation');
const wwydCaseService = require('../cloud/services/wwydCaseService');
const { NotFoundError, AIProviderError } = require('../cloud/utils/errors');
const { WwydCaseStatus } = require('../cloud/schemas/wwydCaseStatus');

function baseCaseState(overrides = {}) {
  return {
    objectId: 'case1',
    ownerId: 'user1',
    status: WwydCaseStatus.ACTIVE,
    originalNarrative: 'narrative',
    casePresentation: 'A short case presentation.',
    startingQuestion: 'What would you do next?',
    conversation: [],
    promptVersion: {},
    aiModel: 'gpt-test',
    aiCostUSD: 0,
    aiTotalTokens: 0,
    createdAt: new Date(),
    updatedAt: new Date(),
    ...overrides,
  };
}

afterEach(() => jest.clearAllMocks());

describe('createCase', () => {
  it('builds and persists a new WWYD case', async () => {
    wwydCaseBuilder.buildCase.mockResolvedValue({
      casePresentation: 'A short case presentation.',
      startingQuestion: 'What would you do next?',
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState();
    wwydCaseRepository.create.mockResolvedValue(created);
    wwydCaseRepository.getById.mockResolvedValue(created);

    const result = await wwydCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(result.status).toBe(WwydCaseStatus.ACTIVE);
    expect(result.casePresentation).toBe('A short case presentation.');
    expect(wwydCaseRepository.create).toHaveBeenCalledWith(
      expect.objectContaining({ ownerId: 'user1', status: WwydCaseStatus.ACTIVE, conversation: [] })
    );
  });

  it('propagates AI provider failures rather than storing a corrupted case', async () => {
    wwydCaseBuilder.buildCase.mockRejectedValue(new AIProviderError('AI provider returned status 500.'));

    await expect(wwydCaseService.createCase({ narrative: 'narrative', ownerId: 'user1' })).rejects.toThrow(AIProviderError);
    expect(wwydCaseRepository.create).not.toHaveBeenCalled();
  });

  it('records AI usage with caseType "wwyd"', async () => {
    wwydCaseBuilder.buildCase.mockResolvedValue({
      casePresentation: 'A short case presentation.',
      startingQuestion: 'What would you do next?',
      meta: { model: 'gpt-4o', usage: { prompt_tokens: 100, completion_tokens: 40, total_tokens: 140 } },
      promptVersion: '1.0.0',
    });
    const created = baseCaseState();
    wwydCaseRepository.create.mockResolvedValue(created);
    wwydCaseRepository.getById.mockResolvedValue(created);
    aiCostRepository.record.mockResolvedValue({ costUSD: 0.001, totalTokens: 140 });

    await wwydCaseService.createCase({ narrative: 'A 68 year old man...', ownerId: 'user1' });

    expect(aiCostRepository.record).toHaveBeenCalledWith(
      expect.objectContaining({ caseId: 'case1', ownerId: 'user1', caseType: 'wwyd', operation: 'buildWwydCase' })
    );
    expect(wwydCaseRepository.incrementAIUsage).toHaveBeenCalledTimes(1);
  });
});

describe('sendMessage', () => {
  it('throws NotFoundError for an unknown case id', async () => {
    wwydCaseRepository.getById.mockResolvedValue(null);

    await expect(
      wwydCaseService.sendMessage({ caseId: 'missing', ownerId: 'user1', message: 'hi' })
    ).rejects.toThrow(NotFoundError);
  });

  it('throws NotFoundError (not a permission error) when the case belongs to a different user', async () => {
    wwydCaseRepository.getById.mockResolvedValue(baseCaseState({ ownerId: 'someone-else' }));

    await expect(
      wwydCaseService.sendMessage({ caseId: 'case1', ownerId: 'user1', message: 'hi' })
    ).rejects.toThrow(NotFoundError);
  });

  it('appends the user message and the AI reply to the conversation', async () => {
    const current = baseCaseState();
    wwydCaseRepository.getById.mockResolvedValueOnce(current).mockResolvedValueOnce({
      ...current,
      conversation: [
        { id: 'm1', role: 'user', text: 'I would order an echo.', createdAt: 'x' },
        { id: 'm2', role: 'assistant', text: 'What does the echo show?', createdAt: 'x' },
      ],
    });
    wwydConversation.continueConversation.mockResolvedValue({
      reply: 'What does the echo show?',
      meta: { model: 'gpt-test' },
      promptVersion: '1.0.0',
    });

    const result = await wwydCaseService.sendMessage({ caseId: 'case1', ownerId: 'user1', message: 'I would order an echo.' });

    expect(wwydConversation.continueConversation).toHaveBeenCalledWith(
      expect.objectContaining({ newMessage: 'I would order an echo.', conversation: [] })
    );
    const [, patch] = wwydCaseRepository.update.mock.calls[0];
    expect(patch.conversation).toHaveLength(2);
    expect(patch.conversation[0]).toMatchObject({ role: 'user', text: 'I would order an echo.' });
    expect(patch.conversation[1]).toMatchObject({ role: 'assistant', text: 'What does the echo show?' });
    expect(result.conversation).toHaveLength(2);
  });
});

describe('getCase', () => {
  it('throws NotFoundError for an unknown case id', async () => {
    wwydCaseRepository.getById.mockResolvedValue(null);

    await expect(wwydCaseService.getCase({ caseId: 'missing', ownerId: 'user1' })).rejects.toThrow(NotFoundError);
  });

  it('returns the case state for a known id owned by the caller', async () => {
    wwydCaseRepository.getById.mockResolvedValue(baseCaseState());

    const result = await wwydCaseService.getCase({ caseId: 'case1', ownerId: 'user1' });

    expect(result.objectId).toBe('case1');
  });
});

describe('listCases', () => {
  it('maps each case to a list row with a derived title', async () => {
    const createdAt = new Date('2026-01-01T00:00:00.000Z');
    const updatedAt = new Date('2026-01-02T00:00:00.000Z');
    wwydCaseRepository.listForOwner.mockResolvedValue([
      baseCaseState({
        objectId: 'case1',
        originalNarrative: 'A 68-year-old man with severe aortic stenosis and worsening dyspnea on exertion.',
        createdAt,
        updatedAt,
      }),
    ]);

    const result = await wwydCaseService.listCases({ ownerId: 'user1' });

    expect(wwydCaseRepository.listForOwner).toHaveBeenCalledWith('user1');
    expect(result).toEqual([
      {
        caseId: 'case1',
        title: 'A 68-year-old man with severe aortic stenosis and worsening …',
        status: WwydCaseStatus.ACTIVE,
        createdAt,
        updatedAt,
      },
    ]);
  });

  it('returns an empty array when the caller owns no cases', async () => {
    wwydCaseRepository.listForOwner.mockResolvedValue([]);

    expect(await wwydCaseService.listCases({ ownerId: 'user1' })).toEqual([]);
  });
});

describe('response formatters', () => {
  it('formatCase omits internal fields like promptVersion/aiModel', () => {
    const formatted = wwydCaseService.formatCase(baseCaseState());
    expect(formatted).not.toHaveProperty('promptVersion');
    expect(formatted).not.toHaveProperty('aiModel');
    expect(formatted.caseId).toBe('case1');
  });

  it('formatFullCase includes promptVersion/aiModel', () => {
    const formatted = wwydCaseService.formatFullCase(baseCaseState());
    expect(formatted).toHaveProperty('promptVersion');
    expect(formatted).toHaveProperty('aiModel');
  });
});
