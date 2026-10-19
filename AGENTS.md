# Agent guide

Standards for AI agents (and people) working on Not at Home. Read these first:

- [`docs/requirements.md`](docs/requirements.md): what the forum does (FR-x, NFR-x).
- [`docs/plan.md`](docs/plan.md): the milestones; work on the one you are asked to.
- [`README.md`](README.md): setup, commands, and how each part works.

## Code

- Follow the surrounding code: same naming, same comment density, same patterns. Look at a similar feature before writing a new one.
- Backend (`backend/`): packages by feature (not by layer), Spring Boot with Maven. Records with `@Schema(requiredMode = REQUIRED)` for responses. Validation errors are `FieldErrorsException` (a 400 with an `errors` map). Format with `./mvnw spotless:apply`.
- Frontend (`frontend/`): Nuxt with TypeScript. Call the backend only through `$api`. Put logic worth testing in `app/utils/` with a Vitest test. Format with `npm run format`.
- Integration tests use `@IntegrationTest` (Testcontainers + PostgreSQL) and the helpers in `com.notathome.support`.
- Comments explain why and cite the requirement (`FR-6.5`), not what the code does.
- Plain, direct English in code, comments, docs and PRs.

## Tooling

- Node comes from nvm and every shell starts fresh, so prefix frontend commands with `source ~/.nvm/nvm.sh && nvm use 24`.
- Docker must be running: backend tests use Testcontainers and `make dev-db` starts PostgreSQL and Mailpit.
- Before opening a PR: `cd backend && ./mvnw spotless:apply verify`, then `cd frontend && npm run format:check && npm run lint && npm run typecheck && npm test`. (`make test` runs the same checks except the format check.)
- After changing the API, start the backend and run `make api-types`, and commit the refreshed `frontend/app/types/api.d.ts`.
- Take screenshots with headless Chrome (`google-chrome --headless`) in light, dark and mobile widths when a page changes.

## Git and pull requests

- Never work on `main`. Each PR gets its own branch from an up-to-date `main`, named after the milestone and the slice: `m6-search-api`, `m6-search-ui`.
- A milestone is delivered as several small PRs, each one working on its own (about 50 files or fewer is the aim; not a hard rule). Backend before frontend when the frontend needs it.
- Commit and PR title: `feat: short description (M6 1/2)`; use `fix:`, `docs:`, `chore:` where they fit. End commit messages with the `Co-Authored-By` trailer and PR bodies with the "Generated with Claude Code" line, as given by the session.
- The PR body says what is in it, which checks were run (and any not run), and notes for the reviewer.
- You branch, commit, push and open the PR with `gh`. Then stop and wait for the review: do not start the next PR until the user says to continue.
- Do not tick the checkboxes in `docs/plan.md`; update the plan text only when the plan itself changes.
- Update the README when setup, commands or behaviour that others rely on change.
