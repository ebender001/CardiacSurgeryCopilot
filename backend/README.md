# Cardiac Surgery Copilot Backend

Back4App Parse Cloud Code backend for **Cardiac Surgery Copilot**, an iOS
app that helps cardiac surgery trainees prepare for the **heart team
conference**: dictate/type a real case; the backend extracts structured
clinical information, asks targeted follow-up questions one at a time,
generates a nine-section report (Diagnosis, Indication, Missing
information, Operative strategy, Alternatives, Controversies, Technical
considerations, Postoperative concerns, Evidence/guidelines with PubMed
references), and -- once a case needs no more follow-up questions -- can
generate three distinct, in-character heart-team-member responses
(surgeon, non-interventional cardiologist, aggressive interventional
cardiologist) so the trainee can practice weighing different specialty
perspectives.

**Back4App owns the case state and AI workflow. The mobile client presents
that workflow to the trainee.** All prompt construction, AI orchestration,
and clinical-workflow logic live here, not in the client.

There is no PHI in this MVP, and no HIPAA-specific handling is implemented.

Every case belongs to an authenticated `Parse.User`. Cloud Functions
resolve the owner from the caller's session (`request.user`) -- never
from a client-supplied id -- and reject access to a case that exists but
belongs to someone else the same way as one that doesn't exist at all.

This backend was scaffolded from a sibling project, **MMCoach**, which
has the same architecture for a single M&M-case-prep workflow. It
originally also included a second, separate "What Would You Do" workflow
(a live case-discussion mode); that was removed to narrow scope back to
the single heart-team conference workflow -- `repositories/aiCostRepository.js`
still tags cost rows with a generic `caseType` string rather than a
strict Parse Pointer for exactly this reason: adding a second workflow
back later needs no schema migration.

---

## Architecture overview

```text
Cloud Function (functions/*.js)
      -> validates input, calls conferenceCaseService, formats the response
      v
conferenceCaseService.js
      -> orchestrates the workflow: create / answer / finalize / heart-team responses / get
      v
ai/*.js
      -> one AI responsibility each, built from prompts/*, validated by schemas/*
      v
aiService.js
      -> the only module that talks to the AI provider (OpenAI)

conferenceCaseRepository.js  |  aiCostRepository.js
      -> the only modules that touch Parse.Object / Parse.Query directly
```

Cloud Functions never call the AI provider or touch Parse objects
directly. Prompts live only in `prompts/`. Model/config values live only
in `config/aiConfig.js`.

### Folder structure

```text
backend/
├── cloud/
│   ├── main.js                          # entry point: registers Cloud Functions only
│   ├── functions/                       # thin Parse.Cloud.define handlers
│   │   ├── createConferenceCase.js      # cscCreateConferenceCase
│   │   ├── answerConferenceQuestion.js  # cscAnswerConferenceQuestion
│   │   ├── finalizeConferenceCase.js    # cscFinalizeConferenceCase
│   │   ├── getConferenceCase.js         # cscGetConferenceCase
│   │   ├── listConferenceCases.js       # cscListConferenceCases
│   │   ├── updateConferenceReport.js    # cscUpdateConferenceReport
│   │   ├── findConferenceReferences.js  # cscFindConferenceReferences
│   │   ├── getConferenceHeartTeamResponses.js # cscGetConferenceHeartTeamResponses
│   │   ├── correctDictation.js          # cscCorrectDictation (shared)
│   │   └── deleteAccount.js             # cscDeleteAccount (shared)
│   ├── services/
│   │   ├── conferenceCaseService.js     # workflow orchestration
│   │   ├── accountService.js            # account deletion
│   │   ├── aiService.js                 # OpenAI HTTP call, retries, timeout, JSON parsing
│   │   ├── referenceService.js          # reference-topic -> pending-reference shape
│   │   └── pubmedService.js             # NCBI E-utilities search + MEDLINE parsing
│   ├── ai/
│   │   ├── conferenceCaseAnalyzer.js    # narrative -> extractedCase; answer -> updated extractedCase
│   │   ├── conferenceQuestionGenerator.js # decides the single next question, or none
│   │   ├── conferenceFinalizer.js       # nine-section report + reference topics
│   │   ├── heartTeamResponses.js        # three in-character heart-team-member responses
│   │   ├── referenceQueryBuilder.js     # topic + searchIntent -> one well-formed PubMed query
│   │   └── dictationCorrector.js        # cleans up one freshly-dictated segment (shared)
│   ├── prompts/
│   │   ├── persona.js                   # CONFERENCE_EDUCATOR_PERSONA
│   │   ├── analyzeConferenceCasePrompt.js
│   │   ├── nextConferenceQuestionPrompt.js
│   │   ├── finalizeConferencePrompt.js
│   │   ├── heartTeamResponsesPrompt.js
│   │   ├── referenceQueryPrompt.js
│   │   └── correctDictationPrompt.js
│   ├── schemas/
│   │   ├── conferenceCaseStatus.js      # collecting_information / ready_to_finalize / completed
│   │   ├── conferenceExtractedCaseSchema.js
│   │   ├── conferenceResponseSchema.js  # validates every Conference AI JSON response
│   │   ├── heartTeamResponseSchema.js   # validates the three-persona AI response
│   │   ├── referenceResponseSchema.js   # validates the PubMed-query-builder response
│   │   └── dictationResponseSchema.js   # validates the dictation-correction response
│   ├── repositories/
│   │   ├── conferenceCaseRepository.js  # CSCConferenceCase Parse persistence
│   │   └── aiCostRepository.js          # CSCCaseAICost Parse persistence (per-call AI cost/token log)
│   ├── utils/
│   │   ├── logger.js
│   │   ├── validation.js
│   │   ├── errors.js                    # AppError subclasses + toParseError()
│   │   ├── idGenerator.js
│   │   └── caseTitle.js
│   └── config/
│       ├── aiConfig.js                  # model name, timeouts, retries -- from env vars
│       └── aiPricing.js                 # USD-per-token pricing table used to cost each AI call
├── public/                              # static hosting (unrelated to Cloud Code)
├── tests/                               # Jest tests, no network / no live OpenAI calls
├── package.json                         # devDependencies only (jest) -- no runtime deps
├── jest.config.js
└── .env.example
```

### Where do I change X?

| Need to change...                             | Look at...                                    |
| ---------------------------------------------- | ---------------------------------------------- |
| How the next follow-up question is selected    | `ai/conferenceQuestionGenerator.js`            |
| The nine-section report generation prompt      | `prompts/finalizeConferencePrompt.js`          |
| The three heart-team-member response personas  | `prompts/heartTeamResponsesPrompt.js`, `ai/heartTeamResponses.js` |
| How Parse persistence works                    | `repositories/conferenceCaseRepository.js`     |
| Cloud Function parameters/validation           | `functions/*.js`, `utils/validation.js`        |
| Model name / timeouts / retries                | `config/aiConfig.js`                           |
| AI cost pricing / which models are priced      | `config/aiPricing.js`                          |
| How a reference topic becomes a PubMed query   | `ai/referenceQueryBuilder.js`, `prompts/referenceQueryPrompt.js` |
| How AI cost is recorded per case                | `repositories/aiCostRepository.js`, `conferenceCaseService.js#recordAIUsage` |
| Case status values                              | `schemas/conferenceCaseStatus.js`              |
| What an AI JSON response must contain           | `schemas/conferenceResponseSchema.js`, `schemas/heartTeamResponseSchema.js` |
| Account deletion (what gets purged)             | `services/accountService.js`                  |

---

## Back4App setup

This project uses the classic Back4App Cloud Code layout (`cloud/`,
`public/`), deployed with the Back4App/Parse CLI.

1. **Environment variables** -- already configured in the Back4App
   dashboard (**App Settings > Server Settings > Environment Variables**):

   | Variable                              | Required | Default   | Purpose |
   | -------------------------------------- | -------- | --------- | ------- |
   | `OPENAI_API_KEY`                       | yes      | (none)    | OpenAI API key. Never committed, never sent to the client. |
   | `OPENAI_MODEL`                         | no       | `gpt-4o`  | Chat Completions model used for all AI calls. |
   | `OPENAI_TIMEOUT_MS`                    | no       | `30000`   | Per-request timeout to the AI provider. |
   | `OPENAI_MAX_RETRIES`                   | no       | `2`       | Retries on 429/5xx or transport errors. |
   | `PUBMED_API_KEY`                       | no       | (none)    | NCBI E-utilities API key -- raises the rate limit from 3 to 10 requests/sec. |
   | `PUBMED_CONTACT_EMAIL`                 | no       | (none)    | Sent as courtesy identification (`tool`/`email` params) per NCBI's usage guidelines. Omitted entirely if not set -- never fabricated. |
   | `CARDIACSURGERYCOPILOT_ADMIN_SECRET`   | only if an admin cost-export function is added later | (none) | Not currently used by any registered Cloud Function -- MMCoach's `mmAdminExportAICosts` admin-cost-export tool was deliberately left out of this MVP scaffold. Already set in the dashboard so that feature can be ported over later without an env var mismatch. |

   See `.env.example` for a local-reference copy of these names (Back4App
   does not read that file; it's documentation only).

2. **Class-Level Permissions (CLP)** -- `CSCConferenceCase` and
   `CSCCaseAICost` are both created automatically the first time they're
   written (Cloud Code uses the master key). Lock down both classes' CLP
   in the dashboard so **no direct client REST/SDK access** is allowed
   (no public find/get/create/update/delete) -- only Cloud Code, which
   uses the master key, can read or write them. Per-object ACLs are
   defense-in-depth for if this is ever loosened, not a substitute for it.

3. **No client OpenAI access** -- the client never receives an OpenAI
   credential, never chooses the model, and never supplies its own system
   prompt. All of that is fixed server-side in `config/aiConfig.js` and
   `prompts/`.

4. **Sign in with Apple** -- in the Back4App dashboard, go to **App
   Settings > Server Settings > Sign In With Apple** and enable it with
   this app's iOS bundle id as the client id.

5. **Email verification** / **password reset email** -- optional, under
   **App Settings > Email Settings**, same as MMCoach.

### Deploying Cloud Code

```bash
cd backend
b4a deploy
```

This uploads `cloud/` and `public/`. `cloud/` has **zero runtime npm
dependencies** (only Node's built-in `https`/`crypto` modules are used),
so no `cloud/package.json` is required for deployment. `backend/package.json`
is for local development/testing only (Jest) and is not deployed.

---

## Authentication

Accounts are plain `Parse.User` records -- there is no custom Cloud Code
for signup/login/password-reset; the iOS client calls `ParseUser` directly.
Cloud Code's only job is to *consume* the resulting session, never to
issue one.

### Ownership enforcement

Every case-touching Cloud Function is defined with `{ requireUser: true }`
*and* independently calls `utils/validation.js#requireAuthenticatedUser`,
which reads `request.user.id` -- never a client param -- as the owner.
`conferenceCaseService.js#getOwnedCase` fetches a case and rejects
(`NotFoundError`) unless `caseState.ownerId` matches the caller, so a
case id alone never grants access to someone else's case, and a
wrong-owner case is indistinguishable from a nonexistent one.

`conferenceCaseRepository.js` also sets a per-object ACL (read/write
restricted to the owning user, no public access) when a case is created.
Cloud Code uses the master key and therefore never actually relies on
this ACL for its own access -- it's defense-in-depth for if the class's
CLP is ever loosened to allow direct client reads.

---

## Parse schemas

### `CSCConferenceCase`

| Field                | Type    | Notes |
| ---------------------- | ------- | ----- |
| `owner`                 | Pointer\<`_User`\> | Set once at creation from `request.user`. |
| `status`                 | String  | `collecting_information` / `ready_to_finalize` / `completed`. See `schemas/conferenceCaseStatus.js`. |
| `originalNarrative`      | String  | The trainee's original dictated/typed text. |
| `extractedCase`          | Object  | Flexible structured case data (diagnosis, indication factors, comorbidities, prior workup/imaging, anatomy, risk factors, patient goals, etc.) -- see `schemas/conferenceExtractedCaseSchema.js`. |
| `conversation`           | Array   | Follow-up Q&A history: `{ questionId, question, category, reason, answer, askedAt, answeredAt }`. |
| `currentQuestion`        | Object\|null | The single active, unanswered question, or `null`. |
| `report`                 | Object\|null | Set at `cscFinalizeConferenceCase`: `{ diagnosis, indication, missingInformation, operativeStrategy, alternatives, controversies, technicalConsiderations, postoperativeConcerns, evidenceGuidelines }`. Each of the first eight is a string (bullet-array responses from the AI are normalized to newline-joined strings); `evidenceGuidelines` is `{ topic, searchIntent, citation: null, verified: false }[]` -- no citation is ever fabricated. |
| `heartTeamResponses`     | Object\|null | Set on first call to `cscGetConferenceHeartTeamResponses` (and cached thereafter): `{ surgeon, nonInterventionalCardiologist, interventionalCardiologist }`, each `{ recommendation, rationale }`. |
| `referenceLookups`       | Object  | Per-topic cache of `cscFindConferenceReferences` results, `{ [topic]: { query, results, cachedAt } }`. |
| `promptVersion`          | Object  | Tracks which prompt version produced each part, e.g. `{ analyze, question, finalize, heartTeamResponses }`. |
| `aiModel` / `aiCostUSD` / `aiTotalTokens` | String\|null / Number / Number | Same running-total pattern as MMCoach. |

### `CSCCaseAICost`

One row per AI provider call. Tags each row with a plain `caseId` string
+ `caseType` (currently always `'conference'`) rather than a Parse
Pointer to `CSCConferenceCase` -- kept generic so a future second
workflow wouldn't need a schema migration to reuse this table.

| Field               | Type    | Notes |
| -------------------- | ------- | ----- |
| `caseId`              | String  | The case this call was made for (not a Pointer -- see above). |
| `caseType`            | String  | Currently always `'conference'`. |
| `owner`               | Pointer\<`_User`\> | Same owner as the case. |
| `operation`           | String  | Which AI call this was, e.g. `analyzeInitialConferenceNarrative`, `generateNextConferenceQuestion`, `finalizeConferenceCase`, `generateHeartTeamResponses`, `buildReferenceQuery`. |
| `model`               | String  | The OpenAI model used. |
| `promptTokens` / `completionTokens` / `totalTokens` | Number | From the provider's `usage` object. |
| `costUSD`             | Number\|null | `null` when `model` isn't in `config/aiPricing.js` -- never a guessed number. |
| `latencyMs`           | Number\|null | The AI call's round-trip time. |

---

## Cloud Functions

All functions are namespaced with a `csc` prefix.

- **`cscCreateConferenceCase`** -- `{ narrative }` -> stores the narrative, extracts structured case information, and either asks one follow-up question or determines the case is `ready_to_finalize`.
- **`cscAnswerConferenceQuestion`** -- `{ caseId, questionId, answer }` -> records the answer, incorporates it, asks another question or transitions to `ready_to_finalize`. `questionId` must match the current active question.
- **`cscFinalizeConferenceCase`** -- `{ caseId }` -> generates the nine-section `report` (see schema above) and sets `status = "completed"`. Rejected if still `collecting_information` or already `completed`.
- **`cscGetConferenceCase`** -- `{ caseId }` -> full client-facing case state.
- **`cscListConferenceCases`** -- no params beyond auth -> every case owned by the caller, most recent first, with a derived title.
- **`cscUpdateConferenceReport`** -- `{ caseId, report }` (partial patch, merged into the existing report) -> lets the trainee hand-edit report text after finalization. Rejected if not yet `completed`.
- **`cscFindConferenceReferences`** -- `{ topic, searchIntent?, caseId? }` -> AI builds a PubMed query from the topic, then searches PubMed live (top 5 by relevance, falling back to the plain topic if the AI-crafted query returns nothing). Read-only lookup -- results aren't written onto the case's `report`, only cached per-topic on `referenceLookups` when `caseId` is given.
- **`cscGetConferenceHeartTeamResponses`** -- `{ caseId }` -> generates (and caches on the case) three distinct, in-character heart-team-member responses. Rejected while the case is still `collecting_information` -- there's nothing stable to generate from yet.
- **`cscCorrectDictation`** -- `{ newSegment, priorNarrative? }` -> cleans up one freshly-dictated narrative segment (speech-recognition term-swap errors only, no rewriting). Not tied to a case id, so its AI usage isn't cost-tracked.
- **`cscDeleteAccount`** -- no params beyond auth -> permanently deletes the caller's account, every `CSCConferenceCase`/`CSCCaseAICost` row they own, and their active Parse sessions.

### Errors

All functions reject with a `Parse.Error` (no stack traces, no internal
details):

| Situation                                   | Parse.Error code |
| --------------------------------------------- | ----------------- |
| No signed-in user (missing/invalid session)   | `INVALID_SESSION_TOKEN` |
| Missing/empty/too-short input                 | `VALIDATION_ERROR` |
| Case not found, malformed caseId, **or case belongs to a different user** | `OBJECT_NOT_FOUND` |
| Stale question, already-completed, still collecting | `OPERATION_FORBIDDEN` |
| AI provider/response problem, or PubMed unreachable/unparseable | `INTERNAL_SERVER_ERROR` |

---

## Testing

```bash
cd backend
npm install
npm test        # runs the Jest suite -- 5 suites, 52 tests, no network / no live Parse or OpenAI calls
npm run lint     # node --check syntax validation on every cloud/*.js file
```

Same mocking approach as MMCoach: `ai/*` modules are tested by mocking
`services/aiService.js`; each service is tested by mocking its repository
and AI modules; each repository is tested against `tests/helpers/fakeParse.js`,
a small in-memory fake of the Parse SDK.

---

## Example workflow

```text
1. cscCreateConferenceCase    { narrative: "A 68-year-old man with severe..." }
   -> { caseId, status: "collecting_information", extractedCase, nextQuestion }
2. cscAnswerConferenceQuestion { caseId, questionId, answer }
   -> ...repeat until...
   -> { caseId, status: "ready_to_finalize", nextQuestion: null }
3. cscGetConferenceHeartTeamResponses { caseId }
   -> { caseId, surgeon, nonInterventionalCardiologist, interventionalCardiologist }
4. cscFinalizeConferenceCase  { caseId }
   -> { caseId, status: "completed", report: { diagnosis, indication, ... } }
5. cscGetConferenceCase       { caseId }  -- full case state, any time
```
