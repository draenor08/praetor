## What this PR does

<!-- One or two sentences. Which feature (FR number) or fix? -->

## Checklist — tick every box before requesting review

- [ ] **Only my package's files.** No files outside the module I own
      (`identity/`, `problem/`, `submission/`, `contest/`, `ws/`, or my Angular slice).
- [ ] **No stray files at the repo root.** No loose `.html`, `.css`, or `.md`
      dumped at the top level. The Angular app lives under `frontend/`.
- [ ] **No private / internal docs committed** (onboarding, working-manual,
      journal, trackers, SRS, `.xlsx`, `.env`).
- [ ] **No shared-file rewrites.** Changes to `styles.scss`, `app.routes.ts`,
      or other shared files are minimal and additive — I did not rewrite them.
- [ ] **It builds.** `docker compose up --build` comes up green (frontend
      compiles, backend starts, no stack traces).
- [ ] **Matches the contract.** Endpoints follow `docs/api-contracts.md`;
      I did not redesign shapes.
- [ ] **Scoped small.** One feature / one concern. Big mixed PRs get sent back.

## How I tested it

<!-- What did you run / click to confirm it works? -->
