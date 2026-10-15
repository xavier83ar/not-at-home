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
- `XSRF-TOKEN`: the CSRF token. JavaScript reads it and sends it back in the `X-XSRF-TOKEN` header on every request that changes state. The backend only issues it on demand (`GET /api/csrf`).

In the frontend, always call the backend through `$api` (`useNuxtApp().$api`): it forwards the session cookie during SSR and adds the CSRF header by itself. `useAuth()` exposes the current user (loaded on every server-side render), `login()` and `logout()`. Backend tests should send the token with `com.notathome.support.Csrf.token(mockMvc)`, not `csrf()` from spring-security-test.

Until registration exists, create a dev user by hand. PostgreSQL can hash the password itself (`docker compose exec postgres psql -U notathome -d notathome`):

```sql
CREATE EXTENSION IF NOT EXISTS pgcrypto;  -- once per database
INSERT INTO users (username, email, display_name, password_hash)
VALUES ('alice', 'alice@example.com', 'Alice', '{bcrypt}' || crypt('a password', gen_salt('bf', 10)));
```

The `{bcrypt}` prefix is required: Spring's password encoder reads it to pick the algorithm. Add `role` (`'ADMIN'`) or `email_verified_at` (`now()`) to the insert if you need them.

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
