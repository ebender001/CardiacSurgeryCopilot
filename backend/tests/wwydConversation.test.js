jest.mock('../cloud/services/aiService');
const aiService = require('../cloud/services/aiService');
const wwydConversation = require('../cloud/ai/wwydConversation');
const { AIResponseError } = require('../cloud/utils/errors');

describe('wwydConversation.continueConversation', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns the AI-drafted reply', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { reply: 'Before choosing, what does the echo say about the aortic valve area?' },
      meta: { model: 'gpt-test', latencyMs: 5, usage: { total_tokens: 45 } },
    });

    const result = await wwydConversation.continueConversation({
      casePresentation: 'A 68-year-old man with severe aortic stenosis.',
      startingQuestion: 'What would you do next?',
      conversation: [],
      newMessage: 'I would order an echo.',
      caseId: 'case1',
    });

    expect(result.reply).toBe('Before choosing, what does the echo say about the aortic valve area?');
    expect(result.meta.usage).toEqual({ total_tokens: 45 });
    expect(result.promptVersion).toBe('1.0.0');
  });

  it('throws AIResponseError when the response has no reply', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: {},
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      wwydConversation.continueConversation({
        casePresentation: 'A case.',
        startingQuestion: 'A question?',
        conversation: [],
        newMessage: 'A message.',
        caseId: 'case1',
      })
    ).rejects.toThrow(AIResponseError);
  });
});
