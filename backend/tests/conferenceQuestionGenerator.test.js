jest.mock('../cloud/services/aiService');
const aiService = require('../cloud/services/aiService');
const conferenceQuestionGenerator = require('../cloud/ai/conferenceQuestionGenerator');
const { AIResponseError } = require('../cloud/utils/errors');

describe('conferenceQuestionGenerator.generateNextQuestion', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns needsQuestion=true with a well-formed question', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: {
        needsQuestion: true,
        question: { text: 'What is the most recent ejection fraction?', category: 'risk factors', reason: 'Affects operative risk.' },
      },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceQuestionGenerator.generateNextQuestion({ extractedCase: {}, conversation: [], caseId: 'c1' });

    expect(result.needsQuestion).toBe(true);
    expect(result.question.text).toBe('What is the most recent ejection fraction?');
  });

  it('returns needsQuestion=false with a null question', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { needsQuestion: false, question: null },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceQuestionGenerator.generateNextQuestion({ extractedCase: {}, conversation: [], caseId: 'c1' });

    expect(result).toEqual(expect.objectContaining({ needsQuestion: false, question: null }));
  });

  it('throws AIResponseError when needsQuestion is true but the question is malformed', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { needsQuestion: true, question: { text: 'Missing category and reason.' } },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      conferenceQuestionGenerator.generateNextQuestion({ extractedCase: {}, conversation: [], caseId: 'c1' })
    ).rejects.toThrow(AIResponseError);
  });

  it('throws AIResponseError when needsQuestion is missing entirely', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: {},
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      conferenceQuestionGenerator.generateNextQuestion({ extractedCase: {}, conversation: [], caseId: 'c1' })
    ).rejects.toThrow(AIResponseError);
  });
});
