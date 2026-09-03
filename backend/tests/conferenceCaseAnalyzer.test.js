jest.mock('../cloud/services/aiService');
const aiService = require('../cloud/services/aiService');
const conferenceCaseAnalyzer = require('../cloud/ai/conferenceCaseAnalyzer');
const { AIResponseError } = require('../cloud/utils/errors');

describe('conferenceCaseAnalyzer.analyzeInitialNarrative', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns a sanitized extractedCase on a well-formed AI response', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { extractedCase: { diagnosis: 'Severe aortic stenosis', relevantImaging: '' } },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceCaseAnalyzer.analyzeInitialNarrative({ narrative: 'case text', caseId: 'c1' });

    expect(result.extractedCase).toEqual({ diagnosis: 'Severe aortic stenosis' });
    expect(result.promptVersion).toBe('1.1.0');
  });

  it('throws AIResponseError when extractedCase is missing', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { somethingElse: true },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      conferenceCaseAnalyzer.analyzeInitialNarrative({ narrative: 'case text', caseId: 'c1' })
    ).rejects.toThrow(AIResponseError);
  });
});

describe('conferenceCaseAnalyzer.incorporateAnswer', () => {
  afterEach(() => jest.clearAllMocks());

  it('merges the AI-updated extractedCase', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: { extractedCase: { diagnosis: 'Severe aortic stenosis', ejectionFraction: '55%' } },
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceCaseAnalyzer.incorporateAnswer({
      extractedCase: { diagnosis: 'Severe aortic stenosis' },
      conversation: [],
      newEntry: { question: 'What is the ejection fraction?', answer: '55%' },
      caseId: 'c1',
    });

    expect(result.extractedCase.ejectionFraction).toBe('55%');
  });
});
