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

## Prerequisites

- JDK 25 (not just the JRE)
- Docker with Compose (PostgreSQL and Mailpit in dev; Testcontainers in tests)

## Running in dev

```sh
make dev-db        # PostgreSQL (5432) and Mailpit (SMTP 1025, web UI http://localhost:8025)
make dev-backend   # API on http://localhost:8080
```

`make dev-backend` runs with the `dev` profile, which also starts the Compose services by itself if they are not up.

Handy URLs while developing:

- Health: <http://localhost:8080/api/health>
- OpenAPI spec: <http://localhost:8080/api/v3/api-docs>
- Swagger UI: <http://localhost:8080/api/swagger-ui.html>

## Tests, formatting and images

```sh
make test      # backend tests (Testcontainers) and format check
make images    # production images
```

Format the backend with `cd backend && ./mvnw spotless:apply`.

## Configuration

Production images read their settings from environment variables; see [`.env.example`](.env.example).

## Repository layout

```
backend/      Spring Boot app (Maven wrapper)
frontend/     Nuxt app (coming next)
docs/         requirements, plan, layout references
design/       brand identity and assets
compose.yaml  local dependencies: PostgreSQL, Mailpit
```
