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

## Quick demo (Docker only)

To try the forum without installing Java, Node or anything else, you only need Docker with Compose:

```sh
cd demo
docker compose up --build
```

The first run builds both images (a few minutes); later runs start in seconds. When it settles:

| | |
|---|---|
| Site | <http://localhost:3000> |
| Emails (Mailpit) | <http://localhost:8025> |
| API docs | <http://localhost:3000/api/swagger-ui.html> |

It starts PostgreSQL, Mailpit, a one-off `seed` container (applies the migrations and creates the demo content: five categories, three topics and two accounts) and then the backend and the frontend, in that order. Sign in with:

| Account | Username | Password |
|---|---|---|
| Admin | `admin` | `admin-password` |
| Member | `rider` | `rider-password` |

Or sign up with any address: the verification email arrives in Mailpit. Everything has a default, so nothing is required; to change ports or credentials, copy [`demo/.env.example`](demo/.env.example) to `demo/.env`. If a port is taken (for example by the dev setup's Mailpit), set `MAILPIT_PORT` or `FRONTEND_PORT` (and `SITE_URL` to match for the latter).

Data lives in Docker volumes and survives `docker compose down`; `docker compose down -v` wipes it, and the next `up` seeds again. Pull new changes and run `docker compose up --build` to update.

This is a demo, not a deployment: no TLS (so the session cookie is not `Secure`), default passwords, Swagger UI on, and the seed credentials are public. The demo has its own Compose project name, so it never touches the dev database from [`compose.yaml`](compose.yaml). For production see [Configuration](#configuration).

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

Pages that need a login or an admin use the route middleware: `definePageMeta({ middleware: 'auth' })` or `'admin'`. Guests are sent to the login page and back afterwards; a logged-in user without the right role gets a 403 (also from the server render). The middleware only decides what is shown: the backend checks permissions on every request.

In the frontend, always call the backend through `$api` (`useNuxtApp().$api`): it forwards the visitor's cookies during SSR (keeping them up to date within one render, which remember-me needs) and adds the CSRF header by itself. `useAuth()` exposes the current user (loaded on every server-side render), `login()` and `logout()`. Backend tests should send the token with `com.notathome.support.Csrf.token(mockMvc)`, not `csrf()` from spring-security-test.

### Accounts in dev

Sign up at <http://localhost:3000/register>. The verification email goes to Mailpit (<http://localhost:8025>); open it there and click the link. People can log in before verifying but can't post (FR-1.9), and the page shows a banner with a "Resend email" button until they do.

Forgot your password? Use <http://localhost:3000/forgot-password>; the email arrives in Mailpit too. The request always answers the same way whether or not the address has an account, so it can't be used to find out who is registered (the lookup, the token and the email all happen on another thread, so the response time doesn't give it away either). Reset links last 1 hour and work once, and a reset logs the user out everywhere: every session and every remembered login, including the browser that finishes the reset. For code that needs the same (password change, ban, demotion), use `UserSessions.endAll(username)`.

To make an account an admin, or to skip the email, edit it directly (`docker compose exec postgres psql -U notathome -d notathome`):

```sql
UPDATE users SET role = 'ADMIN' WHERE username = 'alice';
UPDATE users SET email_verified_at = now() WHERE username = 'alice';
```

For backend code, mark endpoints that need a verified email with `@RequiresVerifiedEmail` (checked against the database on every call, so verifying takes effect in sessions that are already open). Verification links expire after 24 hours and work once; only their hashes are stored.

## Seed data

A fresh database can be seeded with one command (FR-10.3): a default admin, a default user (both already verified, so they can post right away), five categories (Hiking, Climbing, Camping, Gear, Trip Reports) with cover images, and three demo topics (a couple of them with a reply from the other account). It refuses to run if the database already has any users (FR-10.4), so it's safe to run on every deploy, not just the first.

Credentials come from environment variables, never hard-coded:

```sh
SPRING_PROFILES_ACTIVE=dev,seed \
SEED_ADMIN_USERNAME=admin SEED_ADMIN_EMAIL=admin@example.com SEED_ADMIN_PASSWORD='change-me-now' \
SEED_USER_USERNAME=rider SEED_USER_EMAIL=rider@example.com SEED_USER_PASSWORD='change-me-too' \
./mvnw spring-boot:run
```

(in `backend/`; in production, add `seed` to `SPRING_PROFILES_ACTIVE` instead). The `seed` profile is a one-off install step, not a server: it seeds (or logs why it can't, and exits with a non-zero status) and then exits, rather than going on to serve requests.

## Topics and posts

Posts are written with Editor.js and stored as JSON (`posts.content`), plus their plain text (`posts.search_text`, for search in M6). The backend never stores what the browser sends as it is: `EditorContentProcessor` rebuilds the document from the allowed blocks only (paragraph, header, list, quote, code, delimiter, image, link), sanitizes inline HTML (bold, italic, underline, code, mark, and links to this site, http(s) or mailto), and only accepts image blocks that point at one of our own uploads. The frontend sanitizes again when rendering, and renders on the server, so topics read fine without JavaScript. The "link" block is a plain link; there is no preview card, because that would need the server to fetch addresses chosen by users.

- Topic URLs are `/topics/<id>-<slug>` (the id is what is looked up; a wrong slug is redirected) and every post has a permanent link, `/posts/<id>`, that redirects to the right page of its topic and scrolls to the post.
- Deleting is soft. A removed post stays in the topic as "This post was removed" (admins can still read it), a topic whose opening post is deleted while it has no replies is removed entirely, and a removed topic answers with a 410 page listing the category's recent topics. Counters (`reply_count`, last activity, category totals) are recounted in the same transaction as the change, never adjusted by one.
- Editing and deleting are limited to the author for now; admin editing and deleting, locking, pinning and moving topics come with the admin panel (M8).

## Search

`GET /api/search` (public) searches post text and topic titles with PostgreSQL full-text search, English configuration (so `hikes` finds `hiking`). `posts.search_vector` and `topics.title_vector` are generated columns with GIN indexes (`V9__search.sql`); the JPA entities don't map them.

- Parameters: `q` (required, 3 to 100 characters, understood like a web search: quotes for phrases, `-word` to exclude, `or`), and the optional `category` (slug), `author` (username, any case), `from` and `to` (days like `2026-01-31`, UTC, both included), `sort` (`relevance`, the default, or `date`) and `page`. Blank filters are ignored; an unknown category or author just matches nothing. Bad values are a 400 with an `errors` map, like any form.
- One result per matching post, 10 per page. A match on a topic's title belongs to the topic's first post that is still visible (the opening post, unless it was removed), and counts double in the relevance order.
- Removed topics, removed posts and posts of deleted accounts are never returned, not even to admins. Posts of banned users are.
- `snippet` is the matched text as HTML with nothing but `<mark>` tags: the text is cut by PostgreSQL's `ts_headline` with control characters as markers, then escaped as a whole, and only then are the markers turned into `<mark>`.
- The query is plain SQL in `SearchRepository` (the filters are optional, which a fixed JPA query handles badly).

## SEO

Every public page is server-rendered with its own title, description, canonical URL and Open Graph tags (`useSeo()` in `frontend/app/composables/useSeo.ts`; page 2 and up of a list have their own canonical URL). Category, topic and list pages also carry breadcrumb structured data. Topic descriptions are a plain-text excerpt of the opening post; link previews use the category's cover image, or the brand icon when a page has none. Account pages, the admin area and post permalinks are `noindex` and listed in `robots.txt`.

Canonical links and previews need an absolute address: set `NUXT_PUBLIC_SITE_URL` (read at runtime, default `http://localhost:3000`) to the public domain in production. There is no sitemap yet; it is planned for after the first release, once the public domain is known.

## Rate limits

Requests that can be abused are limited (NFR-2). Over a limit the answer is `429` with a `Retry-After` header and, in the body, `code: RATE_LIMITED` and `retryAfterSeconds`; the forms show how long to wait. Limits refill continuously (5 per hour is one every 12 minutes) and live in memory, which is enough for one backend instance.

| Limit | Applies to |
|---|---|
| 10 per minute, per client | log in |
| 5 per hour, per client | sign up, ask for a password reset |
| 10 per hour, per client | reset a password, resend the verification email |
| 30 per hour, per client | verify an email address |
| 5 per hour, per email address | verification emails: the one at sign-up plus every resend |
| 3 per hour, per email address | password reset emails; over the limit nothing is sent and the request isn't told, so the answer never reveals whether an address has an account |
| 30 per hour, per client | upload a file |
| 30 per hour, per client | start a topic or reply |

Add `@RateLimited(RateLimitPolicy.X)` to an endpoint to limit it per client (a new policy is one line in `RateLimitPolicy`). Requests rejected by CSRF protection don't count, reads are never limited, and malformed requests do count.

In local testing you may legitimately sign up or log in more than that. Start the backend with `APP_RATE_LIMIT_ENABLED=false` to switch the limits off (the backend's own tests do this too, except the ones about the limits).

## Uploads

Images (avatars, category covers, post images) are checked by content, not by extension or declared content type: only JPEG, PNG and WebP are accepted, up to 5 MB. Uploading needs a login, but not a verified email, since settings and profile edits don't require one either. Files are stored under a random name and served with `X-Content-Type-Options: nosniff` and a locked-down `Content-Security-Policy`, so a browser never runs one as a script (NFR-2).

Storage is local disk, behind the `UploadStorage` interface (file storage for production is still **TBD**, see `docs/requirements.md` section 10; switching to object storage later means a new implementation, not a change to the rest of the code). The directory comes from `APP_UPLOADS_DIR`; in dev it defaults to `backend/uploads/` (gitignored), so nothing to set up, but production must point it at a volume that survives restarts.

`POST /api/uploads` (`multipart/form-data`, field `file`) returns the file's id and public URL. `GET /api/uploads/<key>` serves it and is public, since guests need to see category covers and avatars too.

### Which client? (read this before deploying)

Every request reaches the backend from the frontend server, so the backend has to be *told* who the visitor is, and it must not believe visitors who lie about it:

- The frontend's `/api` proxy and its server-side rendering **replace** `X-Forwarded-For` with one address, the one that connected to the frontend (`frontend/shared/utils/forwarded.ts`). Whatever the visitor sent is discarded.
- The backend reads that header with Tomcat's `RemoteIpValve` (`server.forward-headers-strategy: native`), and only from proxies it trusts: loopback and private network addresses by default. If the frontend reaches the backend from a public address, list it in `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (a regular expression; it replaces the default list).
- **The backend must not be reachable from the internet except through the frontend.** Anyone who can call it directly from a trusted address can claim to be any client.
- If a reverse proxy sits in front of the frontend (TLS termination, a load balancer), set `NUXT_TRUST_FORWARDED_FOR=true` on the frontend, so it takes the visitor's address from the **last** entry of `X-Forwarded-For`, the one the proxy appended. Only do this if that proxy sets the header itself and nothing can reach the frontend directly; otherwise visitors could pick their own address. Without it, every visitor would count as the proxy.
- IPv6 visitors are limited by their /64 (the first 64 bits), because one customer can switch addresses inside it at will.

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
compose.yaml  local dependencies for dev: PostgreSQL, Mailpit
demo/         Docker-only demo of the whole app (see "Quick demo")
```
