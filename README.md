# Cardiac Surgery Copilot

An iOS app for cardiac surgery trainees with two workflows:

1. **Preoperative Case Conference** — dictate or type a real case, answer a
   few AI-generated follow-up questions, and get a structured prep report:
   Diagnosis → Indication → Missing information → Operative strategy →
   Alternatives → Controversies → Technical considerations → Postoperative
   concerns → Evidence/guidelines (with PubMed references).
2. **What Would You Do** — dictate or type a short real case and get a
   condensed case presentation plus a starting discussion question, then
   hold a live back-and-forth conversation with an AI faculty persona that
   questions your reasoning.

This project reuses proven infrastructure (authentication, dictation/
speech-to-text, on-device PHI screening, Back4App/Parse plumbing, Cloud
Code layering, AI request architecture, subscriptions, website skeleton,
shared UI) from a sibling project, **MMCoach**, adapted for these two new
workflows. See `/Users/ebender/.claude/plans/reflective-toasting-pond.md`
for the scaffolding plan this repo was built from.

```text
CardiacSurgeryCopilot/
├── ios/                # Native iOS/SwiftUI client (Xcode project)
├── backend/             # Back4App Parse Cloud Code backend
├── website/              # Static marketing site (GitHub Pages)
├── docs/                 # Medical dictionaries, screenshots, notes
└── .github/workflows/    # Website deploy workflow
```

- **Backend**: see `backend/README.md` for architecture, Cloud Functions,
  Parse schema, and deployment via the Back4App CLI (`b4a deploy`).
- **Website**: see `website/README.md` for local dev and deploy.
- **iOS**: not yet scaffolded — see the plan's §6–7 for what's reused from
  MMCoach vs. newly built.

## Status

- ✅ Backend shared foundation (AI request architecture, PubMed lookup,
  logging/validation/errors, AI cost tracking) ported from MMCoach.
- ✅ Backend Preoperative Case Conference workflow.
- ✅ Backend What Would You Do workflow.
- ✅ Website (rebranded, both workflows described).
- ⬜ iOS app (auth, dictation/PHI, subscriptions, and both workflows' UI —
  not yet built).
