# Final Project Summary — Job & Skills Matching (JSM)

**Organization:** ENUVEX
**Live system:** <https://enuvex.onrender.com>
**Source:** `Hacking336/ENUVEX`, branch `main`
**Status:** all eight planned phases complete; release verified locally and smoke-tested in production; six documented limitations remain for a larger public rollout.

---

## 1. What JSM does

JSM is a web platform that connects job seekers and employers inside four municipalities of Camarines Norte — **Basud, Daet, Vinzons and Labo** — and scores how well a vacancy fits a particular applicant, explaining the score criterion by criterion.

- **Job seekers** register, record their skills, education, experience, location and preferences, then search vacancies by place, category, skills, salary, distance and match band; view them on a map; read a transparent match percentage; apply; and follow the application through its timeline, interviews and notifications. A skills-gap module shows which local skills they lack and what learning would raise their scores.
- **Employers** register a business profile, request verification, post vacancies that are moderated before publication, review applicants ranked by match score, move them through a hiring pipeline, schedule interviews, and read analytics on how their postings perform, including a CSV export.
- **Administrators** verify employers, approve or reject postings, handle user reports, review automatically detected suspicious listings, suspend or restore accounts, and audit every decision.

## 2. Problems it addresses

| Problem | Response in the system |
| --- | --- |
| Vacancies scattered across tarpaulins and social posts | One searchable, moderated listing with categories, salaries and locations |
| Applicants and posts that do not fit | Deterministic weighted score (skills 40, location 25, employment type 15, experience 10, salary 10) with an explanation of each criterion |
| Distance is decisive in a rural municipality | Barangay-level data, PSGC-aligned boundaries, map with pins, radius/near-me search, adjacency-aware location scoring |
| Applications lost or untraceable | Status lifecycle with an append-only timeline, interviews and notifications |
| No accountability for fake or misleading posts | Employer verification, post approval workflow, user reports, six detection rules, and an immutable audit trail |
| Employers cannot see what works | Funnel, response-time, matching, geographic and per-post analytics with export |

## 3. Major functionality delivered

| Phase | Delivered |
| --- | --- |
| 1 — Geographic mapping | Four municipalities, 125 PSGC barangays, boundary polygons, job pins, map view, near-me radius search |
| 2 — Matching and recommendations | One deterministic engine, five weighted criteria, band classification, explanations, profile completeness, recommendations |
| 3 — Application tracking | Seven statuses, timeline history, interviews with reschedule/cancel, notification inbox with dedupe, saved jobs |
| 4 — Skills gap and guidance | Demand vs supply, gap analysis, what-if simulation, career groups, administrator-curated learning resources |
| 5 — Employer analytics | Nine analytics views, date ranges, per-post performance, CSV export built client-side |
| 6 — Admin moderation | Employer verification, job approval workflow, reports, suspicious listing detection, account suspension, append-only audit log |
| 7 — Security and QA | Admin access-code gate, immediate session revocation on suspension, page-size caps, hardening headers, health readiness, correct error codes, repaired analytics console |
| 8 — Final release | This documentation set, presentation and defense materials, real screenshots, persistent database-backed rate limiting, release verification |

## 4. Architecture in one page

- **One Spring Boot 3.4.2 application on Java 21** serves both the REST API (`/api/**`) and 33 static HTML pages; there is no separate frontend server and no build step for the UI.
- **Layered backend**: controllers (HTTP and authorization), services (business rules), repositories (Spring Data JPA), entities/DTOs; one matching implementation shared by every surface.
- **PostgreSQL** (Supabase in production), schema `enuv`, 17 tables, Flyway migrations V1–V9, `ddl-auto=validate`, non-destructive migrations.
- **Security**: stateless JWT (HS256, 1 hour), BCrypt hashes, role guards at URL and method level, ownership checks in services, hardening headers, and persistent per-address rate limiting on sign-in, registration, reports and applications.
- **Maps**: Leaflet 1.9.4 with tiles from a configurable OpenStreetMap-compatible provider; only posts with a confirmed workplace are plotted.
- **Hosting**: Render (Docker, auto-deploy from `main`), health check `/api/health`; uploads stored in the database so redeploys lose nothing.

## 5. Completion status

| Measure | Result |
| --- | --- |
| Planned phases | 8 of 8 implemented |
| Automated tests | 59 tests, 0 failures (`mvn test`, real PostgreSQL) |
| Scripted API checks | 50/50 security suite, 92/92 and 36/36 moderation suites |
| Migration validation | Fresh database V1–V8 (241 ms) and V9 applied to the development database; `validate` passes |
| Browser verification | Eight role pages with real data; five widths with zero horizontal overflow |
| Production | Deployed and smoke-tested; authenticated production workflows require operator verification (see `RELEASE_CHECKLIST.md` §11) |

## 6. Remaining limitations

**Product**

- Matching is deterministic and rule-based: it cannot judge soft skills or portfolio quality, does not expand synonyms, and depends on complete profiles.
- Posts without a confirmed workplace pin are listed but not mapped.
- There is no chat, no email/SMS delivery, and no password-reset flow; account recovery requires an administrator.

**Security (detailed in `SECURITY_AND_LIMITATIONS.md`)**

1. JWTs live in `localStorage` (XSS would expose a session).
2. No Content-Security-Policy because every page uses inline scripts.
3. No refresh-token endpoint; users re-authenticate after an hour.
4. Three admin list endpoints still paginate in the browser.
5. Swagger UI and `/api-docs` are publicly reachable.
6. Rate limiting is per client address only (shared networks share a budget); the seeded administrator password is a public placeholder that must be rotated.

**Operations**

- Free hosting tier sleeps when idle (first request slower), single instance, no rehearsed backup restore, no load testing, no independent security audit, and no user-acceptance study — the project claims no satisfaction scores or performance benchmarks.

## 7. Recommended next steps after the defense

1. Rotate the seeded administrator password; keep `JSM_ADMIN_ACCESS_CODE` out of source control.
2. Add a CSP and move sessions to HttpOnly cookies with a CSRF token.
3. Add per-account throttling alongside the per-address limiter.
4. Server-side pagination for the remaining admin lists; disable public Swagger in production.
5. PostgreSQL full-text/`pg_trgm` search indexing before the post count grows into the thousands.
6. Automated backups with a rehearsed restore, and dependency scanning in CI.
7. Pilot with a small group of real employers and seekers in one municipality, then measure — with real data — what the current documentation deliberately does not claim.

---

*This summary describes the code as inspected at the end of Phase 8. Where a claim could not be verified in this environment it is marked as untested rather than assumed; see `TESTING_AND_QA_REPORT.md` and `RELEASE_CHECKLIST.md`.*
