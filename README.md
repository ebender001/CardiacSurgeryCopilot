# Heart Team Prep

A native iOS app that helps cardiac surgery trainees prepare for the
**heart team conference**: dictate or type a real case, answer a few
AI-generated follow-up questions, and get a structured prep report —
Diagnosis → Indication → Missing information → Operative strategy →
Alternatives → Controversies → Technical considerations → Postoperative
concerns → Evidence/guidelines (with PubMed references) — plus three
distinct, in-character heart-team-member perspectives on the same case
(a surgeon, a non-interventional cardiologist, and an aggressive
interventional cardiologist).

This project reuses proven infrastructure (authentication, on-device PHI
screening, Back4App/Parse plumbing, Cloud Code layering, AI request
architecture, website skeleton, shared UI) from a sibling project,
**MMCoach**.

An earlier version of this app also had a second, separate "What Would
You Do" workflow (a live case-discussion mode); it was removed to narrow
scope back to the single heart-team conference workflow above. Some
backend field names (`caseType`, `namespace` in `HiddenCaseIdsStore`)
stay deliberately generic as a result, so a second workflow could be
reintroduced later without a schema migration.

```text
CardiacSurgeryCopilot/
├── ios/                  # Native iOS/SwiftUI client (Xcode project)
├── backend/               # Back4App Parse Cloud Code backend
├── website/                # Static marketing site (GitHub Pages)
├── docs/                    # Medical dictionaries, screenshots, notes
└── .github/workflows/        # Website deploy workflow
```

- **Backend**: see `backend/README.md` for architecture, Cloud Functions,
  Parse schema, and deployment via the Back4App CLI (`b4a deploy`).
- **Website**: see `website/README.md` for local dev and deploy.
- **iOS**: see `ios/CardiacSurgeryCopilot/` — open the `.xcodeproj` in Xcode.

## Status

- ✅ Backend: heart team conference workflow (create → follow-up
  questions → nine-section report), heart-team-perspectives generation,
  PubMed reference lookup, account deletion, AI cost tracking.
- ✅ Website (rebranded, single workflow described).
- 🟡 iOS: auth (email + Apple), Home screen, case intake (typed text,
  dictation not yet ported), heart-team-responses screen. Still needed:
  dictation/PHI screening, the real interview loop (answering follow-up
  questions), the finalize/report view, and subscriptions.
