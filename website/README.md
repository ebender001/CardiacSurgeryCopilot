# Heart Team Copilot Website

Public landing page for Heart Team Copilot. Static HTML/CSS/JS -- no
build step, no framework, no npm packages.

```text
website/
├── index.html            # landing page markup
├── privacy-policy.html    # privacy policy (App Store / GDPR / CCPA)
├── terms-of-use.html      # terms of use (includes Apple's required minimum EULA terms)
├── support.html            # FAQ + contact (also the future App Store Connect "Support URL")
├── styles.css              # all styles (CSS custom properties for theme values)
├── script.js                # mobile nav toggle, smooth scroll, footer year
└── assets/                  # empty -- no favicon/hero art exists yet, see below
```

## Run locally

Any static file server works, e.g.:

```bash
cd website
python3 -m http.server 8000
# open http://localhost:8000
```

## Deploy

Deploy the `website/` directory as-is -- no build command, no output
directory remapping needed.

**Vercel**: set the project's Root Directory to `website/`, leave the
Framework Preset as "Other", and leave Build Command / Output Directory
blank (or Output Directory `.`).

**Cloudflare Pages**: set the Build output directory to `website`, and
leave the Build command blank.

**GitHub Pages**: see `.github/workflows/pages.yml` at the repo root.

## Before going live

- **No custom domain is configured yet.** Once one is chosen, add a
  `CNAME` file to this directory (a single line with the domain) and
  point the domain's DNS at GitHub Pages (or whichever host is used),
  and set it in the Pages deploy settings.
- **No favicon, apple-touch-icon, or hero illustration exists yet.**
  `index.html`'s `<head>` intentionally omits `<link rel="icon">`/
  `<link rel="apple-touch-icon">` tags and the hero section is
  copy-only. Once real icon/illustration art exists, add it under
  `assets/` and wire it back in (see MMCoach's `website/index.html` for
  the exact tag pattern to follow).
- Replace the placeholder App Store links (`data-placeholder="app-store"`
  in `index.html`) with the real App Store URL once the app is listed.
- `privacy-policy.html` and `terms-of-use.html`'s Contact sections
  currently assume the same developer entity as MMCoach (Bender Apps,
  LLC / support@benderapps.dev, email only, no mailing address) and the
  same governing law/venue (Missouri) -- confirm these are correct for
  this app. The policy was drafted from this project's *planned* data
  flows (Parse/Back4App, OpenAI, on-device PHI screening, Apple Speech,
  PubMed) -- have it reviewed by counsel familiar with GDPR/CCPA and any
  other regions you operate in before relying on it, and re-check it
  against the actual iOS app's behavior once that's built.
- `terms-of-use.html` includes Apple's required minimum EULA terms
  (App Store Review Guideline 3.1.2) since the app isn't using Apple's
  standard EULA -- if a custom EULA is set in App Store Connect instead,
  that section can be trimmed. No binding arbitration / class-action
  waiver clause is included. Have this reviewed by counsel alongside the
  privacy policy.
- **`terms-of-use.html`'s "Subscriptions" section (and the matching
  support.html FAQ) describe the planned model — first case free, then
  an auto-renewing subscription that unlocks both modes — with Apple's
  required auto-renewal disclosure language, but no specific price or
  billing period yet** (monthly vs. annual, price point). Fill those in
  once the subscription product is configured in App Store Connect, and
  make sure the in-app purchase screen's own disclosure text matches
  this page.
- In-app account deletion (Account → "Delete Account", App Store Review
  Guideline 5.1.1(v)) is planned (mirroring MMCoach's `mmDeleteAccount`
  pattern as `cscDeleteAccount`) but not yet reflected as a completed
  feature anywhere on this site -- it doesn't need to be, since
  `privacy-policy.html` already describes it as available in the App;
  just confirm that's actually true once the iOS app ships.
