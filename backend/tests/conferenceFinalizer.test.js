jest.mock('../cloud/services/aiService');
const aiService = require('../cloud/services/aiService');
const conferenceFinalizer = require('../cloud/ai/conferenceFinalizer');
const { AIResponseError } = require('../cloud/utils/errors');

function fullSections(overrides = {}) {
  return {
    diagnosis: 'Severe symptomatic aortic stenosis.',
    indication: 'Class III symptoms with severe AS by echo criteria.',
    missingInformation: 'Coronary anatomy not yet confirmed by catheterization.',
    operativeStrategy: 'Planned SAVR via median sternotomy.',
    alternatives: 'TAVR was considered but anatomy favors surgical approach.',
    controversies: 'Choice of valve type (mechanical vs bioprosthetic) given age.',
    technicalConsiderations: 'Heavily calcified annulus noted on imaging.',
    postoperativeConcerns: 'Monitor for conduction abnormalities requiring pacing.',
    preponderanceOfEvidence: 'No heart-team responses have been generated for this case yet, so there is nothing to weigh.',
    referenceTopics: [{ topic: 'Surgical aortic valve replacement outcomes', searchIntent: 'Guideline-recommended valve choice by age.' }],
    ...overrides,
  };
}

describe('conferenceFinalizer.finalizeCase', () => {
  afterEach(() => jest.clearAllMocks());

  it('returns all ten report sections plus pending evidence/guideline references', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: fullSections(),
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceFinalizer.finalizeCase({
      extractedCase: {},
      conversation: [],
      originalNarrative: 'narrative',
      caseId: 'c1',
    });

    expect(result.diagnosis).toContain('aortic stenosis');
    expect(result.indication).toBeTruthy();
    expect(result.missingInformation).toBeTruthy();
    expect(result.operativeStrategy).toBeTruthy();
    expect(result.alternatives).toBeTruthy();
    expect(result.controversies).toBeTruthy();
    expect(result.technicalConsiderations).toBeTruthy();
    expect(result.postoperativeConcerns).toBeTruthy();
    expect(result.preponderanceOfEvidence).toBeTruthy();
    expect(result.evidenceGuidelines).toEqual([
      {
        topic: 'Surgical aortic valve replacement outcomes',
        searchIntent: 'Guideline-recommended valve choice by age.',
        citation: null,
        verified: false,
      },
    ]);
  });

  it('accepts a section returned as an array of bullet strings and normalizes it to newline-joined text', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: fullSections({ missingInformation: ['Coronary anatomy unconfirmed.', 'Baseline renal function unclear.'] }),
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    const result = await conferenceFinalizer.finalizeCase({
      extractedCase: {},
      conversation: [],
      originalNarrative: 'n',
      caseId: 'c1',
    });

    expect(result.missingInformation).toBe('Coronary anatomy unconfirmed.\nBaseline renal function unclear.');
  });

  it('throws AIResponseError when a required section is missing', async () => {
    const { operativeStrategy, ...rest } = fullSections();

    aiService.completeJSON.mockResolvedValue({
      data: rest,
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      conferenceFinalizer.finalizeCase({ extractedCase: {}, conversation: [], originalNarrative: 'n', caseId: 'c1' })
    ).rejects.toThrow(AIResponseError);
  });

  it('passes heart-team responses/evidence (or their absence) through into the prompt for preponderanceOfEvidence to reason from', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: fullSections(),
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await conferenceFinalizer.finalizeCase({
      extractedCase: {},
      conversation: [],
      originalNarrative: 'narrative',
      heartTeamResponses: {
        surgeon: { recommendation: 'Proceed with SAVR.', rationale: 'Durability favors surgery for this younger patient.' },
      },
      heartTeamEvidence: {
        surgeon: { pro: { results: [{ title: '20-Year SAVR Durability Outcomes', journal: 'Annals of Thoracic Surgery', year: '2020' }] }, con: { results: [] } },
      },
      caseId: 'c1',
    });

    const { user } = aiService.completeJSON.mock.calls[0][0];
    expect(user).toContain('Proceed with SAVR.');
    expect(user).toContain('20-Year SAVR Durability Outcomes');
    expect(user).toContain('reviewed -- no supporting literature found');
    // nonInterventionalCardiologist/interventionalCardiologist have no response at all -- omitted, not shown as empty.
    expect(user).not.toContain('NON-INTERVENTIONAL CARDIOLOGIST');
  });

  it('tells the prompt plainly when no heart-team responses exist yet, rather than fabricating a summary', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: fullSections(),
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await conferenceFinalizer.finalizeCase({ extractedCase: {}, conversation: [], originalNarrative: 'narrative', caseId: 'c1' });

    const { user } = aiService.completeJSON.mock.calls[0][0];
    expect(user).toContain('Heart-team member responses have not been generated for this case yet');
  });

  it('throws AIResponseError on malformed (non-JSON-shaped) AI output', async () => {
    aiService.completeJSON.mockResolvedValue({
      data: 'not an object',
      meta: { model: 'gpt-test', latencyMs: 5 },
    });

    await expect(
      conferenceFinalizer.finalizeCase({ extractedCase: {}, conversation: [], originalNarrative: 'n', caseId: 'c1' })
    ).rejects.toThrow(AIResponseError);
  });
});
