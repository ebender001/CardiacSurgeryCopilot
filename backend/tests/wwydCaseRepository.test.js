const { createFakeParse } = require('./helpers/fakeParse');

const { Parse, store } = createFakeParse();
global.Parse = Parse;

const wwydCaseRepository = require('../cloud/repositories/wwydCaseRepository');

function baseRecord(overrides = {}) {
  return {
    ownerId: 'user1',
    status: 'active',
    originalNarrative: 'narrative text',
    casePresentation: 'A short case presentation.',
    startingQuestion: 'What would you do next?',
    conversation: [],
    promptVersion: {},
    aiModel: 'gpt-test',
    ...overrides,
  };
}

describe('wwydCaseRepository', () => {
  it('creates a case and returns client-facing JSON', async () => {
    const created = await wwydCaseRepository.create(baseRecord());

    expect(created.objectId).toBeDefined();
    expect(created.ownerId).toBe('user1');
    expect(created.status).toBe('active');
    expect(created.casePresentation).toBe('A short case presentation.');
    expect(created.createdAt).toBeInstanceOf(Date);
  });

  it('initializes AI cost/token totals to zero', async () => {
    const created = await wwydCaseRepository.create(baseRecord());

    expect(created.aiCostUSD).toBe(0);
    expect(created.aiTotalTokens).toBe(0);
  });

  it('incrementAIUsage adds to (not replaces) the running totals across multiple calls', async () => {
    const created = await wwydCaseRepository.create(baseRecord());

    await wwydCaseRepository.incrementAIUsage(created.objectId, { costUSD: 0.01, totalTokens: 150 });
    await wwydCaseRepository.incrementAIUsage(created.objectId, { costUSD: 0.02, totalTokens: 100 });

    const fetched = await wwydCaseRepository.getById(created.objectId);
    expect(fetched.aiCostUSD).toBeCloseTo(0.03, 10);
    expect(fetched.aiTotalTokens).toBe(250);
  });

  it('incrementAIUsage advances the token total even when costUSD is null (unpriced model)', async () => {
    const created = await wwydCaseRepository.create(baseRecord());

    await wwydCaseRepository.incrementAIUsage(created.objectId, { costUSD: null, totalTokens: 75 });

    const fetched = await wwydCaseRepository.getById(created.objectId);
    expect(fetched.aiCostUSD).toBe(0);
    expect(fetched.aiTotalTokens).toBe(75);
  });

  it('restricts the saved object ACL to the owning user, with no public access', async () => {
    const created = await wwydCaseRepository.create(baseRecord());
    const acl = store.get(created.objectId).acl;

    expect(acl.publicRead).toBe(false);
    expect(acl.publicWrite).toBe(false);
    expect(acl.readAccess.has('user1')).toBe(true);
    expect(acl.writeAccess.has('user1')).toBe(true);
  });

  it('retrieves a previously created case by id', async () => {
    const created = await wwydCaseRepository.create(baseRecord());
    const fetched = await wwydCaseRepository.getById(created.objectId);

    expect(fetched).not.toBeNull();
    expect(fetched.objectId).toBe(created.objectId);
    expect(fetched.originalNarrative).toBe('narrative text');
  });

  it('returns null for a missing or malformed case id', async () => {
    expect(await wwydCaseRepository.getById('does-not-exist')).toBeNull();
  });

  it('updates only the given fields and preserves the rest', async () => {
    const created = await wwydCaseRepository.create(baseRecord());
    const updated = await wwydCaseRepository.update(created.objectId, {
      conversation: [{ id: 'm1', role: 'user', text: 'Hi', createdAt: 'x' }],
    });

    expect(updated.conversation).toHaveLength(1);
    expect(updated.originalNarrative).toBe('narrative text');
    expect(updated.casePresentation).toBe('A short case presentation.');
  });

  it('defaults conversation to an empty array', async () => {
    const created = await wwydCaseRepository.create(baseRecord({ conversation: undefined }));

    expect(created.conversation).toEqual([]);
  });

  describe('listForOwner', () => {
    // Distinct owner ids from every other test in this file -- the fake
    // Parse store is shared module-wide with no reset between tests.
    it('returns only the given owner\'s cases, most recent first', async () => {
      const wait = () => new Promise((resolve) => setTimeout(resolve, 2));

      await wwydCaseRepository.create(baseRecord({ ownerId: 'listforowner-user', originalNarrative: 'first' }));
      await wait();
      await wwydCaseRepository.create(baseRecord({ ownerId: 'listforowner-other', originalNarrative: 'not this one' }));
      await wait();
      await wwydCaseRepository.create(baseRecord({ ownerId: 'listforowner-user', originalNarrative: 'second' }));

      const cases = await wwydCaseRepository.listForOwner('listforowner-user');

      expect(cases).toHaveLength(2);
      expect(cases.map((c) => c.originalNarrative)).toEqual(['second', 'first']);
      expect(cases.every((c) => c.ownerId === 'listforowner-user')).toBe(true);
    });

    it('returns an empty array for an owner with no cases', async () => {
      expect(await wwydCaseRepository.listForOwner('listforowner-nobody')).toEqual([]);
    });
  });
});
