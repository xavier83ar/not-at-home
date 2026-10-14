# Not at Home: Implementation Plan

> **Status:** Draft v0.1, open for review <br>
> **Last updated:** 2026-10-14 <br>
> **Based on:** [requirements v0.4](requirements.md)

This plan turns the requirements into ordered milestones. Each milestone is a working, testable slice of the forum, built through backend, frontend and tests together, so the app runs end to end after every one.

Requirement IDs (FR-x, NFR-x, section numbers) refer to [`requirements.md`](requirements.md).

---

## 1. How we work

### 1.1 Milestones and pull requests
- Milestones are done in order. Each one depends on the previous ones.
- Each milestone is delivered as one or more pull requests to `main`. A PR should be small enough to review in one sitting, so larger milestones are split by task.
- `main` always builds, passes CI and runs locally.

### 1.2 Definition of done (every milestone)
- [ ] Backend endpoints have integration tests (Testcontainers + PostgreSQL) for the main flows and permission checks.
- [ ] Frontend pages have component tests for non-trivial logic, and render correctly with SSR (checked with JavaScript disabled for public pages).
- [ ] New endpoints appear in the OpenAPI spec, and the frontend's generated types are refreshed.
- [ ] Lint and formatting pass on both services.
- [ ] Pages work on mobile and desktop, in light and dark mode.
- [ ] The README is updated if setup or commands changed.

### 1.3 Conventions decided up front
| Topic | Convention |
|---|---|
| API base path | `/api`, no version prefix for now (one frontend, same repo). |
| API errors | Spring's standard `ProblemDetail` (RFC 9457) JSON, with field errors for validation failures. |
| Pagination | `?page=` (1-based in the UI) and a fixed page size of 10 (FR-7.1). Responses include items, page, total pages and total items. |
| Dates | Stored and sent as UTC ISO-8601; formatted in the browser (NFR-7). |
| IDs and URLs | Numeric IDs plus slugs in URLs, e.g. `/c/hiking`, `/t/123-best-trails-in-patagonia`. The ID is what's looked up; a wrong slug redirects to the right one. |
| Editor.js content | Stored as JSON. The backend validates it against an allow-list of block types (FR-4.4), sanitizes inline HTML, and extracts plain text into `search_text` on save. |
| Backend packages | By feature (`auth`, `users`, `categories`, `topics`, `posts`, `search`, `moderation`, `uploads`), not by layer. |
| Package manager | npm for the frontend; Maven wrapper for the backend. |

---

## 2. Milestones overview

| # | Milestone | Main requirements |
|---|---|---|
| M0 | Project foundation | Section 4, NFR-5, NFR-6 |
| M1 | Accounts and authentication | FR-1 (except Google), NFR-2 |
| M2 | Google sign-in | FR-1.5 |
| M3 | Categories, uploads and seed data | FR-2, FR-10.3, FR-10.4 |
| M4 | Topics and posts | FR-3, FR-4, FR-5, FR-7 |
| M5 | Home page, navigation and SEO | Section 8, NFR-1 |
| M6 | Search | FR-6 |
| M7 | Profiles and account settings | FR-8 |
| M8 | Moderation and admin panel | FR-9, FR-10.1, FR-10.2, FR-5.8 |
| M9 | Polish and release readiness | NFR-1 to NFR-4, open TBDs |

M6 (search) and M7 (profiles) only depend on M4, so they can be swapped or worked on in parallel.

---

## 3. Milestones

### M0. Project foundation
Goal: both services run in dev, talk to each other through `/api`, and CI builds both images. No forum features yet.

**Repository**
- [ ] Root `.gitignore` (IDE folders such as `.idea/`, build outputs, `node_modules`, `.env` files).
- [ ] Root `README.md`: what the project is, prerequisites (Java, Node, Docker), how to run everything.
- [ ] `compose.yaml` with PostgreSQL and Mailpit.
- [ ] Optional `Makefile` with `dev-db`, `dev-backend`, `dev-frontend`, `test`, `images`.
- [ ] `.env.example` listing every environment variable both services read.

**Backend (`backend/`)**
- [ ] Generate from Spring Initializr with Maven: Web, Validation, Data JPA, PostgreSQL driver, Flyway, Security, OAuth2 Client, Session JDBC, Mail, Docker Compose support, Testcontainers. Pick the current Spring Boot release and the newest Java LTS it supports, and record both in the README.
- [ ] Add springdoc-openapi; spec served at `/api/v3/api-docs` in dev.
- [ ] Flyway baseline migration (empty schema plus Spring Session tables).
- [ ] `GET /api/health` (or Actuator's health endpoint under `/api`).
- [ ] Profiles: `dev` (Compose support on, Mailpit) and `prod` (everything from environment variables).
- [ ] Formatting and lint (e.g. Spotless with google-java-format).
- [ ] One Testcontainers integration test proving the app starts against PostgreSQL.

**Frontend (`frontend/`)**
- [ ] Create the Nuxt app with TypeScript, ESLint and Prettier.
- [ ] Bootstrap SCSS themed with the brand tokens from `design/brand/README.md` (Pine, Ember, fonts) for light and dark mode. BootstrapVueNext for interactive components.
- [ ] Load the Montserrat and Barlow fonts (self-hosted or through Nuxt's font module) and add the favicon and app icons from `design/brand/logo/`.
- [ ] Default layout: top bar from section 8.1 (logo, nav links, search box placeholder, user menu placeholder), footer, theme toggle that follows the system setting by default.
- [ ] `/api/**` proxied to the backend through `routeRules`, with the backend URL from an environment variable.
- [ ] A small API client wrapper that, during SSR, forwards the incoming `Cookie` header to the backend and passes any `Set-Cookie` back to the browser.
- [ ] Script to generate TypeScript types from the backend's OpenAPI spec (e.g. openapi-typescript).
- [ ] Vitest set up with one sample test.
- [ ] Multi-stage `Dockerfile` that builds Nuxt and runs its Node server.

**CI**
- [ ] `.github/workflows/backend.yml`: on changes to `backend/**`, run format check, tests, and `./mvnw spring-boot:build-image`.
- [ ] `.github/workflows/frontend.yml`: on changes to `frontend/**`, run lint, type check, tests, and the Docker build.

**Done when:** `docker compose up`, the backend and the frontend start in dev; the home placeholder page shows a value fetched from the backend through `/api`; both workflows pass.

---

### M1. Accounts and authentication
Goal: people can register, verify their email, log in and out, stay logged in, and reset their password.

**Backend**
- [ ] Migrations: `users`, `email_verification_tokens`, `password_reset_tokens`, remember-me `persistent_logins`.
- [ ] Spring Security configuration: session-based login with JSON endpoints (`POST /api/auth/login`, `POST /api/auth/logout`), sessions in PostgreSQL (Spring Session JDBC), cookie `HttpOnly`, `Secure` in prod, `SameSite=Lax`.
- [ ] CSRF protection using a cookie-held token that the frontend sends back in a header (Spring Security's recommended setup for JavaScript clients).
- [ ] `GET /api/me`: the current user (or 401), used by the frontend on every SSR request.
- [ ] Registration with validation (FR-1.1 to FR-1.3): unique case-insensitive username and email, username rules, password length. Passwords hashed with Spring Security's default (bcrypt) encoder.
- [ ] Email verification (FR-1.9): token emailed on registration, verify endpoint, resend endpoint. Unverified users can log in but get 403 on posting endpoints.
- [ ] "Remember me" for 30 days using persistent tokens (FR-1.7).
- [ ] Password reset (FR-1.8): request endpoint (always answers the same, so it doesn't reveal whether an email exists), single-use expiring token, reset endpoint that also ends other sessions.
- [ ] Banned users can't log in and get a clear error (FR-1.10). (The ban action itself comes in M8.)
- [ ] Rate limiting on login, registration, password reset and, later, posting (NFR-2), e.g. with Bucket4j in memory, which is enough for one backend instance.
- [ ] Email templates (plain HTML) using the brand logo and colors.

**Frontend**
- [ ] Pages: Register, Log in, Verify email (landing page for the link), Forgot password, Reset password.
- [ ] Auth state loaded from `/api/me` during SSR and kept in a store; user menu shows the user, guests see "Log in" and "Sign up".
- [ ] Route middleware for pages that need login or admin.
- [ ] Banner for unverified users with a "resend email" action; "New topic" button disabled for them (section 8.1).
- [ ] Form validation messages that show the backend's field errors.

**Done when:** a new user can register, receive the verification email in Mailpit, verify, log out, log back in with "remember me", restart the browser and still be logged in, and reset a forgotten password.

---

### M2. Google sign-in
Goal: FR-1.5 works for both new and existing accounts.

- [ ] Google OAuth2 client registration through Spring Security, with credentials from environment variables. Redirect URI on the frontend's domain (`/api/login/oauth2/code/google`), and the backend configured to trust forwarded headers from the proxy so generated URLs are correct.
- [ ] Existing email: log in and store `google_id` on the account.
- [ ] Unknown email: keep the Google profile (email, name, picture) in the session, redirect to the registration page in "Google" mode. The form is pre-filled, the email is locked, and no password is asked. Submitting creates a verified account and logs the user in.
- [ ] Download the Google profile picture as the user's avatar (uses the upload storage from M3, so this step can land after M3).
- [ ] Banned accounts are rejected here too.
- [ ] "Continue with Google" button on the Log in and Register pages.
- [ ] Document how to create the Google OAuth credentials in the README.

**Done when:** a new Google user lands on a pre-filled form, picks a username and is logged in without any email; an existing user with the same email is logged straight in.

---

### M3. Categories, uploads and seed data
Goal: admins manage categories with cover images, and a fresh install has demo content.

**Uploads**
- [ ] A storage interface with a local-disk implementation (file storage is TBD for production, so the rest of the code must not care where files live).
- [ ] Upload endpoint that checks type (JPEG, PNG, WebP, by content, not just extension) and size (5 MB), stores files under random names, and records them in `uploads`.
- [ ] Files served from a path under `/api` (or a separate `/media` path) with headers that prevent them from being run as scripts (NFR-2).
- [ ] Optional resizing for avatars and category covers, so pages stay fast (NFR-3).

**Categories**
- [ ] Migration: `categories`.
- [ ] Public endpoints: list categories with topic count, post count and latest activity (FR-2.2); get one by slug.
- [ ] Admin endpoints: create, edit (including cover image), reorder, soft delete. Deleting is refused while the category has active topics (FR-2.4).
- [ ] Frontend: "All categories" page; admin category management (list, form, drag or arrow reordering).

**Seed data (FR-10.3, FR-10.4)**
- [ ] A seed command (e.g. a Spring Boot `seed` profile or command-line flag) that creates the default admin, default user, default categories with cover images (Hiking, Climbing, Camping, Gear, Trip Reports) and 3 demo topics.
- [ ] Admin and user credentials come from environment variables; the command refuses to run if the database already has users.
- [ ] Demo topics are created after M4 adds topics; until then the seed creates users and categories only.

**Done when:** an admin can create a category with an image and see it on the All categories page, and a fresh database can be seeded with one command.

---

### M4. Topics and posts
Goal: the core forum. Users start topics, reply, quote, edit and delete; everything renders server-side.

**Backend**
- [ ] Migrations: `topics`, `posts` (with `search_text`), `uploads` link to posts if needed.
- [ ] Editor.js content handling: allow-list of blocks (paragraph, header, list, quote, code, link, delimiter, image), inline HTML sanitized (e.g. with the OWASP Java HTML Sanitizer), image blocks may only reference our own uploads, plain text extracted into `search_text`.
- [ ] Create topic (FR-3.1) in a single transaction with its opening post; title max 128 characters.
- [ ] List topics in a category: pinned first, then by latest activity, paginated (FR-3.2, FR-3.3, FR-7.1).
- [ ] Get a topic and its posts, oldest first, paginated (FR-4.3). Endpoint to find which page a post is on, for permalinks (FR-4.6).
- [ ] Reply (FR-4.1); refused on locked topics for everyone (FR-3.4). Updates `reply_count` and `last_post_at`.
- [ ] Edit own post, setting `edited_at` (FR-5.1).
- [ ] Delete own post and the opening-post rules (FR-5.2, FR-5.3); soft deletes everywhere (FR-5.5); counts and "last activity" recomputed.
- [ ] Removed posts returned as placeholders to normal users, with full content to admins (FR-5.6, FR-5.8).
- [ ] Removed topic response with the 5 most recently active topics in the category (FR-5.7).
- [ ] Rate limit on posting.
- [ ] Add the 3 demo topics to the seed command.

**Frontend**
- [ ] Editor.js wrapper component, client-only, with the enabled tools and an image tool that uses the upload endpoint.
- [ ] Server-side renderer: Vue component that turns Editor.js JSON into HTML, so topics are fully readable without JavaScript (section 4.3).
- [ ] Pages: Category (topic list), Topic (posts, pagination, reply form at the bottom), New topic (category picker, title, editor).
- [ ] Post actions: quote (inserts a quote block with author and link, FR-4.5), copy link, edit in place, delete with confirmation.
- [ ] "Edited" marker, "This post was removed" placeholder, Admin badge (FR-4.2).
- [ ] Jump to last page and links to a topic's latest post (FR-7.2).
- [ ] Removed topic page (FR-5.7).
- [ ] Relative dates with the full date on hover (NFR-7).

**Done when:** a verified user can create a topic, others can reply and quote, posts with images render server-side, and every delete rule in FR-5 behaves as specified.

---

### M5. Home page, navigation and SEO
Goal: the front door looks like the design reference and search engines can index the forum.

- [ ] Backend: highlighted categories endpoint (top 4 by posts in the last 30 days, filled by all-time posts), latest 5 topics, most active 5 users (last 30 days, excluding deleted and banned).
- [ ] Home page (section 8.2): hero with category cards using their cover images, latest topics column (2/3) and most active users column (1/3).
- [ ] Breadcrumbs on category and topic pages (NFR-1).
- [ ] Top bar finished: working search box (submits to the search page from M6), "New topic", full user menu, mobile collapse.
- [ ] SEO: titles and descriptions per page with `useSeoMeta`, canonical URLs, Open Graph tags (topic title, category image), `robots.txt`, sitemap of categories and topics.
- [ ] Empty states for categories without topics and for a fresh forum.

**Done when:** the home page matches the design notes, and viewing the page source of home, category and topic pages shows the full content and meta tags.

---

### M6. Search
Goal: FR-6 with PostgreSQL full-text search.

- [ ] Migration: a generated `tsvector` column on posts (from `search_text`) and on topic titles, with GIN indexes. English text search configuration.
- [ ] Search endpoint: keywords, filters by category, author and date range, sort by relevance (default) or date, paginated (FR-6.1 to FR-6.4).
- [ ] Snippets with the matched words highlighted (`ts_headline`), sanitized before display.
- [ ] Deleted topics, deleted posts and posts from deleted accounts are excluded (FR-6.5).
- [ ] Search results page with filters, linking to the matching post's permalink (FR-6.2).
- [ ] Search page is server-rendered but marked `noindex`, so search engines don't index result pages.

**Done when:** searching a word from a reply finds it with a highlighted snippet, filters narrow the results, and deleted content never shows.

---

### M7. Profiles and account settings
Goal: FR-8.

- [ ] Public profile page (FR-8.1): username, display name, avatar, role badge, join date, post count, profile content, recent topics and posts.
- [ ] Settings page: edit display name, avatar upload, profile content with Editor.js (FR-8.2). Generated initials avatar when none is set.
- [ ] Change email with re-verification of the new address (FR-8.3); the old address stays active until the new one is verified.
- [ ] Change password, requiring the current one, and ending other sessions (FR-8.3).
- [ ] Set a password for Google-only accounts (FR-1.8 path, available from settings too).
- [ ] Delete account (FR-8.4): confirm with password or Google, soft delete, anonymize email and username, delete posts following FR-5.3, show "Deleted user" where the name appeared, end all sessions. The last admin can't delete their account (FR-8.5).

**Done when:** a user can personalize their profile, change email and password, and delete their account with the forum still reading correctly afterwards.

---

### M8. Moderation and admin panel
Goal: admins can keep the forum clean (FR-9, FR-10).

- [ ] Migration: `reports`.
- [ ] Report a post with a reason (FR-9.1); one open report per user per post.
- [ ] Admin reports queue: open reports with the post (including removed content), dismiss or resolve by editing or deleting the post, or banning the author (FR-9.2).
- [ ] Topic moderation: lock / unlock, pin / unpin, move to another category (FR-3.4, FR-3.5), from the topic page.
- [ ] Users admin: search and list users; ban and unban (FR-9.3), which ends the user's sessions and remember-me tokens immediately (section 4.5); promote and demote with the "at least one admin" guard (FR-9.4).
- [ ] Stats: number of users, topics and posts (FR-10.2).
- [ ] Admin panel layout and navigation (FR-10.1), visible only to admins and protected on the server.

**Done when:** an admin can handle a report end to end, ban a user who is logged in elsewhere and see them logged out on their next request, and can't demote the last admin.

---

### M9. Polish and release readiness
Goal: everything in the non-functional requirements is checked, and the remaining TBDs are decided.

- [ ] Error pages (404, 403, 500) and friendly empty states everywhere (NFR-1).
- [ ] Accessibility pass: keyboard navigation, focus states, labels, alt text, contrast in both themes (NFR-4), e.g. with axe checks.
- [ ] Responsive pass on mobile, tablet and desktop.
- [ ] Performance check: first load under 2 seconds on key pages (NFR-3); add database indexes where queries are slow.
- [ ] Security review: authorization on every endpoint, CSRF, upload handling, rate limits, security headers (NFR-2).
- [ ] End-to-end smoke tests for the main journeys (e.g. Playwright): register, verify, post, reply, search, report and moderate. Nice-to-have, per section 4.8.
- [ ] Production configuration documented: every environment variable, how to run migrations and the seed, how to build and run both images.
- [ ] Decide the remaining TBDs (requirements section 10): hosting, production email provider, file storage. Add a deploy step to both workflows once hosting is chosen.

**Done when:** the forum is ready to deploy as soon as hosting is chosen.

---

## 4. Risks and things to watch

| Risk | Mitigation |
|---|---|
| Session cookies and CSRF through the Nuxt proxy during SSR are easy to get subtly wrong. | Build and test the cookie-forwarding client in M0 and login in M1 before any feature depends on them. |
| Google OAuth behind a proxy produces wrong redirect URLs. | Configure forwarded-header handling in M2 and test the full flow locally through the Nuxt proxy, not against the backend directly. |
| Editor.js JSON is user input and could carry XSS. | Validate block types and sanitize on the server when saving, and render with our own component instead of raw HTML from the client. |
| Editor.js only runs in the browser. | Keep the editor client-only and the renderer server-side from the first post (M4). |
| Counters (`reply_count`, last activity, post counts) drift after deletes and moves. | Update them in the same transaction as the change, and cover deletes and moves with tests. |
| File storage for production is undecided. | Hide it behind the storage interface from M3, so switching to object storage later is one new implementation. |

---

## 5. Open points for this plan

1. **Milestone order:** M6 (search) and M7 (profiles) can be swapped. *Default: as listed.*
2. **Pull request size:** one PR per milestone, or several smaller PRs per milestone (e.g. backend and frontend separately)? *Default: several smaller PRs, each one working on its own.*
3. **End-to-end tests:** keep them as a nice-to-have in M9, or start them in M1? *Default: M9.*
