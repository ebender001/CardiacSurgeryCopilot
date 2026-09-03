jest.mock('../cloud/services/aiService');
const aiService = require('../cloud/services/aiService');
const wwydCaseBuilder = require('../cloud/ai/wwydCaseBuilder');
const { AIResponseError } = require('../cloud/utils/errors');

describe('wwydCaseBuilder.buildCase', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns the AI-built case presentation and starting question', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: {
        casePresentation: 'A 68-year-old man presents with severe aortic stenosis and worsening dyspnea.',
        startingQuestion: 'What would you want to know before recommending TAVR versus SAVR?',
      },
      meta: { model: 'gpt-test', latencyMs: 5, usage: { total_tokens: 60 } },
    });

    const result = await wwydCaseBuilder.buildCase({ narrative: 'A 68 year old man with severe AS...', caseId: 'case1' });

    expect(result.casePresentation).toBe('A 68-year-old man presents with severe aortic stenosis and worsening dyspnea.');
    expect(result.startingQuestion).toBe('What would you want to know before recommending TAVR versus SAVR?');
    expect(result.meta.usage).toEqual({ total_tokens: 60 });
    expect(result.promptVersion).toBe('1.0.0');
  });

  it('throws AIResponseError when the response is missing startingQuestion', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { casePresentation: 'A case presentation.' },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      wwydCaseBuilder.buildCase({ narrative: 'A case narrative.', caseId: 'case1' })
    ).rejects.toThrow(AIResponseError);
  });
});
