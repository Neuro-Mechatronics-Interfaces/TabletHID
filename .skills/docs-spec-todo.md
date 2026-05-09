# Website/Spec/TODO Documentation Upkeep

Use this role for documentation workflow, status matrix maintenance, backlog hygiene, and public website copy under `web/`.

## Source Of Truth

- `spec/platform-feature-status.md` is canonical for current Android/iOS feature status.
- `TODO.md` is the product-wide backlog and Android tracker.
- `ios/TODO.md` holds iOS-specific parity, validation, and release tasks.
- Website files describe what users can do and must not overstate platform support.

## Update Workflow

1. Identify the feature area in `spec/platform-feature-status.md`.
2. Compare Android and iOS status before changing claims.
3. Update the status row when a feature is implemented, changed, deferred, or validated.
4. Move TODO items only when the implementation and validation state justify it.
5. Review website impact using the project schema in `AGENTS.md`.

## Website Map

- Product overview and core feature list: `web/src/pages/Home.jsx`
- Setup, walkthroughs, troubleshooting, FAQ, logs: `web/src/pages/Support.jsx`
- Privacy, local storage, permissions, Bluetooth behavior, logging: `web/src/pages/Privacy.jsx`
- Routes/navigation/footer: `web/src/App.jsx`, `web/src/components/Nav.jsx`, `web/src/components/Footer.jsx`
- Static metadata: `web/index.html`, `web/public/robots.txt`, `web/public/sitemap.xml`
- Screenshots/static images: `web/img/`

## Quality Bar

- Keep Android and iOS cells explicit when behavior differs.
- Use statuses such as `Implemented`, `Ported`, `Initial`, `Experimental`, `TODO`, or a concise qualifier.
- Mention validation gaps directly, for example `Implemented; physical-device validation pending`.
- If `web/` changes, run `npm run build` from `web/`; run `npm install` only if dependencies are missing or package files changed.
- Do not commit `web/dist/` unless the repository already tracks it for deployment.
