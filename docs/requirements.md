# Not at Home: Forum Requirements

> **Status:** v0.5, ready for planning <br>
> **Last updated:** 2026-10-15 <br>
> **Repository:** https://github.com/xavier83ar/not-at-home

Items marked **TBD** are still to be decided. None of them block the implementation plan (see section 10).

Items marked **Post-MVP** are wanted but are built after the first release (see section 1.4 and the plan's M10). The first release has email and password login only.

---

## 1. Overview

### 1.1 Purpose
"Not at Home" is a web forum for outdoor enthusiasts. People can register, start discussion topics, and reply to each other, in a clean interface with its own outdoor brand.

### 1.2 Goals
- Give outdoor enthusiasts a simple place to share trips, ask questions and talk gear.
- Look and feel like an outdoor brand, not a generic forum.
- Stay simple to run: a small community (see NFR-3) on a single PostgreSQL database.

### 1.3 Out of scope (for now)
- Private messaging between users
- Real-time features (live updates, chat)
- Notifications of any kind, including unread markers (former FR-11)
- Native mobile apps
- CAPTCHA on sign-up
- Moderation log of admin actions
- Temporary user suspensions (admins can only ban and unban)
- Database backups
- Multiple UI languages

### 1.4 Post-MVP
Planned for after the first release; everything else in this document works without them:
- Log in or sign up with Google (FR-1.5), and everything that exists only because of it: the OAuth2 Client dependency, `google_id`, password-less accounts in the settings and account deletion flows, and the Google avatar.
- A generated sitemap of categories and topics (section 4.3). It needs the final public domain, so it is added once hosting is chosen; everything else in SEO is in the first release.

---

## 2. Brand

### 2.1 Name and slogan
- **Name:** Not at Home
- **Slogan:** "Stories from the trail"

### 2.2 Logo and identity
Final and approved. The full brand guide is [`design/brand/README.md`](../design/brand/README.md), with every asset in `design/brand/logo/`. In short:
- **Logo:** two mountain peaks with a trail winding up the front one, over the wordmark. Full, horizontal (top bar), mark-only and favicon versions, each in light, dark and one-color variants.
- **Colors:** Pine `#1F4D3A` (primary) and Ember `#E06A2C` (accent), plus black and white, with tints for dark mode and small text.
- **Fonts:** Montserrat for headings, Barlow for body text (Google Fonts).
- **Bootstrap tokens:** the brand guide includes the Sass variables and dark-mode overrides the frontend theme starts from.

---

## 3. Glossary

| Term | Meaning |
|------|---------|
| Category | A top-level section grouping related topics, with a name, description and cover image (e.g. "Hiking", "Gear"). |
| Topic / Thread | A discussion started by a user, with a title and an opening post. |
| Post / Reply | A message inside a topic. The first post is the topic's opening post. |
| Guest | A visitor who is not logged in. |
| User | A registered member with standard permissions. |
| Admin | A member with full moderation and management permissions. |
| Banned | A user permanently blocked from logging in. |

---

## 4. Architecture and Technology Stack

### 4.1 Overview
Two services in one repository:
- **Frontend:** a Nuxt app (Vue 3 + TypeScript) that renders pages on the server (SSR). A public forum needs good SEO, so every public page must arrive as complete HTML.
- **Backend:** a Spring Boot REST API backed by PostgreSQL.

The browser only talks to the frontend's domain. Requests to `/api/**` are proxied to the backend, so the website and the API share one origin: no CORS setup, and login cookies just work. In dev and by default in production, Nuxt does the proxying (a server handler, so the backend URL is read at runtime). A reverse proxy in front of both services can take over once hosting is chosen.

### 4.2 Repository layout
No monorepo tooling (Nx or similar); plain folders are enough at this size.
```
backend/      Spring Boot app (Maven wrapper)
frontend/     Nuxt app + Dockerfile
docs/         requirements, plan, layout references (docs/design-reference/)
design/       brand identity and assets (design/brand/)
compose.yaml  local dependencies: PostgreSQL, Mailpit
.github/workflows/backend.yml   runs on changes to backend/**
.github/workflows/frontend.yml  runs on changes to frontend/**
```

### 4.3 Frontend (Nuxt)
- Nuxt with file-based routing. All public pages (home, categories, topics, profiles, search) are server-rendered.
- SEO: page titles and meta tags with `useSeoMeta`, readable URLs with slugs (e.g. `/topics/123-best-trails-in-patagonia`), canonical URLs, and a generated sitemap.
- Bootstrap's SCSS, themed with the brand tokens from the brand guide (section 2.2) for light and dark mode. Interactive components (dropdowns, modals) come from BootstrapVueNext or plain Vue, not Bootstrap's own JS.
- Editor.js runs only in the browser, as a client-only component. Saved posts are rendered from their Editor.js JSON to sanitized HTML during SSR, so search engines see the content.
- Talks to the API only through `/api`. During SSR it forwards the visitor's session cookie to the backend.

### 4.4 Backend (Spring Boot)
Follow Spring Boot's standard approach, starting from Spring Initializr with Maven:
- **Spring Web** for the REST API and **Validation** for request checks.
- **Spring Data JPA** for data access, **Flyway** for migrations.
- **Spring Security** for password login, authorization and CSRF protection. **OAuth2 Client** for Google login is added after the first release (FR-1.5, post-MVP).
- **Spring Session JDBC** to store sessions in PostgreSQL (section 4.5).
- **Spring Mail** for verification and password reset emails.
- **Spring Boot Docker Compose support**, which starts PostgreSQL and Mailpit from `compose.yaml` when the app runs in dev.
- **springdoc-openapi** to publish the API contract (section 4.6).
- **Testcontainers** for integration tests against a real PostgreSQL.
- Java version: the current LTS supported by the chosen Spring Boot version (TBD in the plan).

### 4.5 Authentication and sessions
**Decision: server-side sessions**, not JWTs.
- Spring Security issues a session cookie (`HttpOnly`, `Secure`, `SameSite=Lax`) after login. Sessions are stored in PostgreSQL with Spring Session JDBC, so restarts and deploys don't log users out.
- "Remember me" uses Spring Security's persistent remember-me tokens, stored in the database (about 30 days).
- Post-MVP: Google login will use Spring Security's OAuth2 Client and end in the same kind of session.
- Logout, password change, ban or demotion invalidate the user's sessions and remember-me tokens immediately.
- Mutating requests are protected with Spring Security's CSRF token, which the frontend sends in a header.

**Trade-offs considered (sessions vs. JWT):**
- *Sessions (chosen):* simplest to build with Spring Security, and they can be revoked instantly (bans, logout, password change). The cost is a database lookup per request and a session table, which is negligible at this scale (NFR-3).
- *JWT (rejected for now):* a stateless API with no session lookups, useful for scaling out or for non-browser clients such as a mobile app. But a JWT cannot be revoked before it expires, so a safe setup needs short-lived access tokens plus refresh tokens stored in the database and cookies anyway, plus refresh logic during SSR. That is more moving parts for benefits we don't need yet.
- If a mobile app or a public API is added later, token-based auth can be added alongside sessions.

### 4.6 API contract
- The backend publishes an OpenAPI spec with springdoc-openapi.
- The frontend generates its TypeScript API types from that spec (e.g. with openapi-typescript), so the two services don't drift apart.

### 4.7 Development and build
- **Dev:** `docker compose up` (or let Spring Boot start it) for PostgreSQL and Mailpit, then `./mvnw spring-boot:run` in `backend/` and `npm run dev` in `frontend/`. A small Makefile can wrap these commands.
- **Backend image:** `./mvnw spring-boot:build-image`, Spring Boot's built-in Buildpacks support, with no Dockerfile to maintain.
- **Frontend image:** a multi-stage Dockerfile that builds the Nuxt app and runs its Node server.
- Both images are production-ready and configured through environment variables (database URL, mail settings, backend URL for the proxy; Google OAuth credentials once Google login is added).

### 4.8 CI/CD and testing
- Two GitHub Actions workflows, each triggered only by changes in its own folder.
- Each runs lint, tests and the production image build. Publishing and deploying are added once hosting is chosen.
- Backend tests: JUnit 5 with Spring Boot Test and Testcontainers.
- Frontend tests: Vitest with Vue Test Utils. End-to-end tests (e.g. Playwright) are a nice-to-have.

### 4.9 Stack summary

| Layer | Choice |
|-------|--------|
| Frontend | Nuxt (Vue 3 + TypeScript), SSR |
| UI | Bootstrap (SCSS, brand theme, light/dark) + BootstrapVueNext |
| Rich text editor | [Editor.js](https://editorjs.io/), content stored as JSON |
| Backend | Java + Spring Boot (Maven), REST API |
| Database | PostgreSQL, migrations with Flyway |
| Search | PostgreSQL full-text search |
| Authentication | Spring Security: password login (Google OAuth2 post-MVP), server sessions (Spring Session JDBC) |
| API contract | OpenAPI (springdoc) + generated TypeScript types |
| Email | Spring Mail; Mailpit in dev; production provider **TBD** (required for verification and password reset) |
| File storage | **TBD**: local disk vs. object storage (e.g. S3), for avatars, category images and post images |
| Containers | Backend: Buildpacks (`spring-boot:build-image`); frontend: Dockerfile |
| CI/CD | GitHub Actions, one workflow per service |
| Hosting / deployment | **TBD** |

---

## 5. Users and Roles

The forum is **public**: guests can read everything that is not deleted.

### 5.1 Guest
- Browse categories, topics, posts and public profiles.
- Use search.
- Register and log in.

### 5.2 User
Everything a guest can do, plus:
- Create topics and reply, once their email is verified.
- Quote and link to posts.
- Edit and delete their own posts (FR-5).
- Manage their own profile and account, including deleting it.
- Report posts to admins.

### 5.3 Admin
Everything a user can do, plus:
- Create, edit, reorder and delete categories.
- Edit or delete any post or topic.
- Lock / unlock, pin / unpin and move topics between categories.
- Review and resolve reported posts.
- Manage users: view, ban, unban, promote to admin, demote to user.
- See the content of removed posts (FR-5.8).
- See basic forum stats.

### 5.4 Permissions matrix

| Action | Guest | User (unverified email) | User | Admin |
|--------|:-----:|:-----:|:----:|:-----:|
| View categories, topics, posts, profiles | ✓ | ✓ | ✓ | ✓ |
| Search | ✓ | ✓ | ✓ | ✓ |
| Register / log in | ✓ | | | |
| Log out | | ✓ | ✓ | ✓ |
| Create topic / reply | | | ✓ | ✓ |
| Edit / delete own post | | | ✓ | ✓ |
| Edit own profile, delete own account | | ✓ | ✓ | ✓ |
| Report a post | | | ✓ | ✓ |
| Edit / delete any post or topic | | | | ✓ |
| Lock / pin / move topics | | | | ✓ |
| Manage categories | | | | ✓ |
| Manage users, handle reports, view stats | | | | ✓ |
| See content of removed posts | | | | ✓ |

Banned users cannot log in at all, so they can only do what a guest can.

---

## 6. Functional Requirements

### FR-1 Registration and Authentication
- FR-1.1 Register with username, display name, email and password.
- FR-1.2 Usernames and emails are unique (case-insensitive). Username rules: 3 to 30 characters, letters, digits, `_` and `-`. Usernames cannot be changed after registration; the display name can.
- FR-1.3 Passwords: at least 8 characters, stored hashed (see NFR-2), never in plain text.
- FR-1.4 Log in with username or email plus password.
- FR-1.5 **(Post-MVP)** Log in or sign up with Google:
  - A Google login with an email that already has an account logs into that account, and links the Google identity to it. If that account's email was never verified, linking first removes its password and ends its sessions, so whoever registered the address cannot keep access.
  - A Google login with an unknown email is a shortcut to registration: it opens the registration form pre-filled with what Google provides (email, display name, and profile picture as the avatar). The user picks a username and can change the display name. The email field is locked to the Google address, which counts as verified, so no verification email is sent. No password is needed; the user can set one later (FR-1.8).
- FR-1.6 Log out.
- FR-1.7 "Remember me" option that keeps the user logged in across browser restarts for 30 days.
- FR-1.8 Password reset via an email link with an expiring, single-use token. Once Google login exists (FR-1.5), accounts created with Google and no password can use it to set one.
- FR-1.9 Email verification on registration. Users can log in before verifying, but cannot post, reply or report until they do. They can request a new verification email. Accounts created through Google are already verified (FR-1.5, post-MVP).
- FR-1.10 Banned users cannot log in, by password (or by Google, once FR-1.5 exists), and see a message saying the account is banned.

### FR-2 Categories
- FR-2.1 The forum is organized into categories, each with a name, description, cover image and display order.
- FR-2.2 The "All categories" page lists every category with its image, topic count, post count and latest activity.
- FR-2.3 Only admins can create, edit, reorder or delete categories.
- FR-2.4 A category with active (non-deleted) topics cannot be deleted. An admin must first delete its topics or move them to another category. Deleting a category is a soft delete.

### FR-3 Topics / Threads
- FR-3.1 A verified user can create a topic in a category with a title (max 128 characters) and an opening post.
- FR-3.2 A category page lists its topics with title, author, reply count and last activity.
- FR-3.3 Pinned topics appear first; the rest are sorted by latest activity.
- FR-3.4 Locked topics can be read but nobody, admins included, can reply until they are unlocked.
- FR-3.5 Admins can move a topic to another category.

### FR-4 Replies / Posts
- FR-4.1 A verified user can reply to an unlocked topic.
- FR-4.2 Posts show the author (display name, avatar, and an "Admin" badge for admins), date and content.
- FR-4.3 Posts in a topic are shown in chronological order, oldest first, so the opening post is at the top of page 1.
- FR-4.4 Posts are written with Editor.js. Enabled blocks: paragraph, header, list, quote, code, link, delimiter, and image. Images are uploaded (JPEG, PNG or WebP, max 5 MB each). Content is stored as Editor.js JSON and sanitized on the server and when rendered, to prevent XSS.
- FR-4.5 Quote another post in a reply: inserts a quote block with the quoted author and a link back to the original post.
- FR-4.6 Each post has a permanent link that opens the right page of the topic and scrolls to it.

### FR-5 Editing and Deleting
- FR-5.1 Users can edit their own posts at any time; edited posts show an "edited" marker with the time.
- FR-5.2 Users can delete their own posts at any time.
- FR-5.3 Deleting the opening post:
  - If the topic has no replies, the whole topic is deleted.
  - If it has replies, the topic stays with its title and replies, and the opening post shows "This post was removed".
- FR-5.4 Admins can edit or delete any post or topic. Deleting a topic deletes all its posts.
- FR-5.5 All deletes are soft deletes (a `deleted_at` timestamp). Deleted content is hidden from lists, search, counts and profiles, for everyone except admins as described in FR-5.8.
- FR-5.6 A deleted post inside a visible topic shows "This post was removed" in its place, so replies that quote it still make sense.
- FR-5.7 Opening a deleted topic shows "This topic has been removed" and lists the 5 most recently active topics in the same category.
- FR-5.8 Admins can see the original content of removed posts, clearly labelled as removed, including in removed topics. This helps them handle reports. Restoring deleted content is not part of the MVP.

### FR-6 Search
- FR-6.1 Keyword search across topic titles and post content, using PostgreSQL full-text search.
- FR-6.2 Results show topic title, matching snippet, author, category and date, and link to the matching post.
- FR-6.3 Filters: by category, by author, by date range.
- FR-6.4 Results are paginated (FR-7) and can be sorted by relevance (default) or date.
- FR-6.5 Deleted topics and posts, and posts from deleted accounts, never appear in results.

### FR-7 Pagination
- FR-7.1 Topic lists, posts in a topic and search results are paginated, 10 items per page.
- FR-7.2 Users can jump to the last page of a topic, and topic lists link to each topic's latest post.

### FR-8 User Profiles and Account
- FR-8.1 Public profile page: username, display name, avatar, role badge, join date, post count, profile content and recent topics and posts.
- FR-8.2 Users can edit their display name, avatar (uploaded image) and profile content (written with Editor.js). Users without an avatar get a generated one (e.g. their initials).
- FR-8.3 Users can change their email (the new address must be verified) and password (requires the current password).
- FR-8.4 Users can delete their account, after confirming with their password (or Google, once FR-1.5 exists). The account is soft deleted and its email and username are anonymized, so the same email can register again. Where their name would appear, it shows "Deleted user". All their posts are deleted following FR-5.3, so topics they started with replies from others remain.
- FR-8.5 The last remaining admin cannot delete their account or be demoted (see FR-9.4).

### FR-9 Moderation
- FR-9.1 Verified users can report a post with a reason.
- FR-9.2 Admins see a queue of open reports and can dismiss them, or act on them (edit or delete the post, or ban the author), which resolves the report.
- FR-9.3 Admins can ban a user and unban them at any time. Banning ends the user's sessions immediately (section 4.5). A banned user's existing posts stay visible.
- FR-9.4 Admins can promote a user to admin or demote an admin to user. There must always be at least one admin.

### FR-10 Admin Panel
- FR-10.1 A dedicated area for admins to manage categories, users and reports.
- FR-10.2 Basic forum stats: number of users, topics and posts.
- FR-10.3 A seed script, run during installation, creates:
  - a default admin
  - a default user
  - default categories, with cover images (e.g. Hiking, Climbing, Camping, Gear, Trip Reports)
  - 3 demo topics
- FR-10.4 The seed script never runs against a database that already has data, and default credentials are set at install time rather than hard-coded.

---

## 7. Non-Functional Requirements

### NFR-1 UI / UX ("it must look nice")
- Clean, modern, consistent design following the brand identity (section 2).
- Layout references: [`docs/design-reference/`](design-reference/design-notes.md). Page layouts are described in section 8.
- Responsive: works well on mobile, tablet and desktop.
- Light and dark mode, following the system setting by default with a manual toggle.
- Clear navigation with breadcrumbs (Home › Category › Topic).
- Friendly empty states, error pages (404, 403, 500) and form validation messages.

### NFR-2 Security
- Passwords hashed with a modern algorithm (bcrypt or argon2).
- Protection against XSS, CSRF (Spring Security's CSRF tokens, plus `SameSite` cookies) and SQL injection.
- Basic rate limiting on login, registration, password reset and posting. CAPTCHA is out of scope.
- Uploaded files are checked for type and size, and served so they cannot run as scripts.
- HTTPS in production.
- Authorization is checked on the server for every action, not only hidden in the UI.

### NFR-3 Performance and Scale
- First page load under 2 seconds.
- Expected scale is small: under 100 users, about 300 posts per month, under 10 concurrent visitors.

### NFR-4 Accessibility
- Aim for WCAG 2.1 AA: keyboard navigation, sufficient contrast in both themes, form labels and image alt text.

### NFR-5 Data
- Database migrations managed in version control with Flyway.
- Times are stored in UTC.

### NFR-6 Maintainability and Quality
- Automated tests (unit and integration) for core flows, using the tools in section 4.8.
- Linting and formatting enforced on backend and frontend.
- README with setup instructions for running both services in dev, building the images, and running the seed script.

### NFR-7 Language and Dates
- English only.
- Dates are shown in a relative format ("2 hours ago") in the visitor's time zone, with the full date on hover.

### NFR-8 Privacy
- Store only the personal data needed.
- Email addresses are never shown publicly.

---

## 8. Key Pages / Screens

### 8.1 Top bar (every page)
Based on the design reference:
- **Left:** logo, then navigation links (Home, Categories).
- **Center:** search box.
- **Right:** "New topic" button and user menu (profile, settings, admin panel for admins, theme toggle, log out). Guests see "Log in" and "Sign up" instead. Unverified users see the "New topic" button disabled with a hint to verify their email.
- On mobile, the navigation links collapse into a menu.

### 8.2 Home
- **Hero:** highlighted categories as cards with their cover image as background, name and topic count. Shows the 4 categories with the most posts in the last 30 days, filled with the remaining categories by all-time posts if fewer than 4 are active. Link to "All categories".
- **Below, two columns (2/3 + 1/3):**
  - Latest topics (5), with author avatar, title, author, last activity and reply count, and a link to see more.
  - Most active users (5), by posts in the last 30 days, excluding deleted and banned accounts.

### 8.3 Other pages
- All categories
- Category (topic list)
- Topic (posts and reply form)
- New topic
- Search results
- Register / Log in / Verify email / Forgot password / Reset password
- User profile and account settings
- Admin panel: categories, users, reports, stats
- Removed topic page (FR-5.7)
- Error pages (404, 403, 500)

---

## 9. Data Model (initial sketch)

To be refined in the plan. All tables have `created_at`; soft-deletable tables have `deleted_at`. Spring Session and Spring Security's remember-me add their own tables (sessions and persistent logins), created by Flyway migrations.

- **User**: id, username, email, email_verified_at, password_hash (nullable for Google-only accounts, post-MVP), google_id (nullable; unused until Google login, cleared when an account is deleted), role (user/admin), display_name, avatar_url, profile_content (Editor.js JSON), status (active/banned), last_seen_at, deleted_at
- **Category**: id, name, slug, description, image_url, position, deleted_at
- **Topic**: id, category_id, author_id, title, slug, is_pinned, is_locked, last_post_at, reply_count, deleted_at
- **Post**: id, topic_id, author_id, content (Editor.js JSON), search_text (plain text for full-text search), edited_at, deleted_at
- **Report**: id, post_id, reporter_id, reason, status (open/dismissed/resolved), resolved_by, resolved_at
- **EmailVerificationToken**: id, user_id, email, token_hash, expires_at, used_at
- **PasswordResetToken**: id, user_id, token_hash, expires_at, used_at
- **Upload** (post images): id, owner_id, url, content_type, size

---

## 10. Open Questions

None. All earlier questions are answered and folded into the sections above.

These items are still **TBD** but don't block the plan. Dev runs locally with Mailpit and local disk, so they only matter before release:
- Hosting and deployment target.
- Production email provider.
- File storage for uploads (local disk vs. object storage).
- Exact Java and Spring Boot versions, to be fixed when the backend is scaffolded.

---

## 11. Milestones

- To be defined in the implementation plan (`docs/plan.md`).
