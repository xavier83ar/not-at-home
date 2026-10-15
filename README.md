# Not at Home

> Stories from the trail

A web forum for outdoor enthusiasts. Nuxt frontend (SSR) and Spring Boot REST API over PostgreSQL, in one repository.

- Requirements: [`docs/requirements.md`](docs/requirements.md)
- Implementation plan: [`docs/plan.md`](docs/plan.md)
- Brand: [`design/brand/README.md`](design/brand/README.md)

## Versions

| | |
|---|---|
| Java | 25 (LTS) |
| Spring Boot | 4.1.1 |
| Node | 24 (LTS), pinned in `frontend/.nvmrc` |
| Nuxt | 4.6 (Vue 3, TypeScript) |
| Bootstrap | 5.3 with BootstrapVueNext |

## Prerequisites

- JDK 25 (not just the JRE)
- Node 24 and npm (`nvm use` in `frontend/` picks the right version; current Nuxt needs Node 22.19+ or 24)
- Docker with Compose (PostgreSQL and Mailpit in dev; Testcontainers in tests)

## Running in dev

```sh
make dev-db        # PostgreSQL (5432) and Mailpit (SMTP 1025, web UI http://localhost:8025)
make dev-backend   # API on http://localhost:8080
make dev-frontend  # site on http://localhost:3000 (run `npm install` in frontend/ the first time)
```

Open <http://localhost:3000>. The browser only talks to the frontend; `/api/**` is proxied to the backend (set `NUXT_BACKEND_URL` if it is not on `localhost:8080`).

`make dev-backend` runs with the `dev` profile (the default), which also starts the Compose services by itself if they are not up.

Handy URLs while developing:

- Health: <http://localhost:8080/api/health>
- OpenAPI spec: <http://localhost:8080/api/v3/api-docs>
- Swagger UI: <http://localhost:8080/api/swagger-ui.html>

## Tests, formatting and images

```sh
make test      # backend tests (Testcontainers) and format check; frontend lint, type check and tests
make images    # production images (backend with Buildpacks, frontend with its Dockerfile)
```

Format the backend with `cd backend && ./mvnw spotless:apply` and the frontend with `cd frontend && npm run format`.

### API types

The frontend's TypeScript types are generated from the backend's OpenAPI spec and committed in `frontend/app/types/api.d.ts`. After changing the API, start the backend and run:

```sh
make api-types
```

## Authentication

Login uses server-side sessions kept in PostgreSQL (Spring Session). The browser holds two cookies:

- `SESSION`: the session id (`HttpOnly`, `SameSite=Lax`, `Secure` in production).
- `remember-me`: only if the user ticked "Keep me logged in" (`HttpOnly`, `SameSite=Lax`, 30 days). It brings the session back after the browser was closed. The cookie holds a random series and token that change every time it is used; the current pair lives in the `persistent_logins` table, so a copied cookie stops working as soon as the real owner comes back. Reusing an old token revokes all of that user's remembered logins, except that the previous token still works for 10 seconds: a browser that restores several tabs after a restart sends the same cookie from all of them at once, and that must not look like theft. Logging out forgets them on every device.
- `XSRF-TOKEN`: the CSRF token. JavaScript reads it and sends it back in the `X-XSRF-TOKEN` header on every request that changes state. The backend only issues it on demand (`GET /api/csrf`).

Banned accounts can't log in: the right password gets a 403 with code `ACCOUNT_BANNED`, a wrong one the usual 401, so a ban is never visible to someone who doesn't know the password. Their remember-me cookies are rejected and cleared. (The ban action itself comes with the admin panel; until then, `UPDATE users SET status = 'BANNED' WHERE username = '...'`.)

In the frontend, always call the backend through `$api` (`useNuxtApp().$api`): it forwards the visitor's cookies during SSR (keeping them up to date within one render, which remember-me needs) and adds the CSRF header by itself. `useAuth()` exposes the current user (loaded on every server-side render), `login()` and `logout()`. Backend tests should send the token with `com.notathome.support.Csrf.token(mockMvc)`, not `csrf()` from spring-security-test.

### Accounts in dev

Sign up at <http://localhost:3000/register>. The verification email goes to Mailpit (<http://localhost:8025>); open it there and click the link. People can log in before verifying but can't post (FR-1.9), and the page shows a banner with a "Resend email" button until they do.

To make an account an admin, or to skip the email, edit it directly (`docker compose exec postgres psql -U notathome -d notathome`):

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'alice';
UPDATE users SET email_verified_at = now() WHERE username = 'alice';
```

For backend code, mark endpoints that need a verified email with `@RequiresVerifiedEmail` (checked against the database on every call, so verifying takes effect in sessions that are already open). Verification links expire after 24 hours and work once; only their hashes are stored.

## Continuous integration

Two GitHub Actions workflows run on pull requests and on pushes to `main`, each only when its own folder changes:

- `backend.yml`: format check, tests (Testcontainers) and the Buildpacks image build.
- `frontend.yml`: lint, format check, type check, tests and the Docker image build.

Images are built but not published or deployed yet; that comes once hosting is chosen. Local image names: `not-at-home-backend:<version>` and `not-at-home-frontend`.

## Configuration

Production images read their settings from environment variables; see [`.env.example`](.env.example).

## Repository layout

```
backend/      Spring Boot app (Maven wrapper)
frontend/     Nuxt app + Dockerfile
docs/         requirements, plan, layout references
design/       brand identity and assets
compose.yaml  local dependencies: PostgreSQL, Mailpit
```
