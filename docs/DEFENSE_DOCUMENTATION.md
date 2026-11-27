# JSM — System Documentation for Defense

**System:** Job & Skills Matching (JSM), deployed under the ENUVEX product name
**Live system:** https://enuvex.onrender.com
**Repository:** `Hacking336/ENUVEX`, branch `main`, audited commit `b207ee0`
**Document basis:** source code, database migrations, the automated test suite and the read-only production audit in `docs/final-release/PHASE_9_ACCEPTANCE_TEST_REPORT.md` (2026-11-26). Every number below was produced by the system, not estimated.
**Fields to complete by the group:** panel/course/school, group member names, adviser, date of defense. They are deliberately left blank here — this document does not invent them.

---

## 1. Abstract

JSM is a web-based job and skills matching system for four neighbouring municipalities of Camarines Norte — **Basud, Daet, Vinzons and Labo**. In these municipalities hiring is mostly informal: vacancies appear on tarpaulins, social-media posts, word of mouth and walk-ins, so job seekers see only what they happen to hear about, and employers receive applications that do not fit the post in skills, location or availability.

JSM gives both sides one structured channel. Seekers record skills, education, work experience, location and employment preferences, then search moderated vacancies, see them on a barangay-level map, and receive a **transparent match percentage** that explains itself criterion by criterion. Employers post vacancies that are reviewed before publication, read applicants ranked by the same match score, move them through a hiring pipeline with interview scheduling, and see analytics on how their posts perform. Administrators verify employers, moderate posts, handle reports, review automatically flagged suspicious listings, suspend or restore accounts, and leave an audit trail for every decision.

The matching score is a **deterministic weighted formula**, not machine learning: skills 40 %, location 25 %, employment type 15 %, experience 10 %, salary 10 %, with criteria that cannot be judged removed from both sides of the calculation. The same profile and the same vacancy always produce the same score, which is what lets the seeker's list, the employer's applicant ranking and the administrator's statistics agree — and what makes the result explainable to the person who receives it.

---

## 2. Problem and objectives

### 2.1 Problem statement

| # | Problem | Consequence |
| --- | --- | --- |
| 1 | Vacancies are scattered across informal channels | Seekers miss posts; employers re-advertise |
| 2 | No structured skills comparison | Both sides spend time on unsuitable applications |
| 3 | Barangay-level distance is rarely stated | A reachable job looks unreachable, and vice versa |
| 4 | Applications are verbal or by message | No recorded status, timeline or interview |
| 5 | No accountability for misleading or duplicate posts | Fake or stale listings stay visible |
| 6 | Employers cannot see post performance | No feedback on where candidates are lost |

### 2.2 General objective

To design and develop a web-based job and skills matching system for Basud, Daet, Vinzons and Labo, Camarines Norte, that connects local job seekers with local employers through structured profiles, geographic job search and a transparent skills-based match score, with moderation that keeps listings credible.

### 2.3 Specific objectives → delivered as

| Objective | Delivered as |
| --- | --- |
| Structured seeker profiles | Skills, education, work experience, location, employment-type and salary preferences, with a completeness indicator |
| Verifiable employer identity | Business profile + administrator verification status |
| Moderated vacancies | Employer posts enter `PENDING_REVIEW`; approval is required for publication |
| Explainable matching | Five weighted criteria, each returning score, points, status and a sentence |
| Geographic search | 125 barangays across four municipalities, boundary polygons, map pins, radius/near-me search |
| Application tracking | `SUBMITTED → REVIEWING → SHORTLISTED → INTERVIEW → ACCEPTED / REJECTED`, plus a terminal `WITHDRAWN`, with an append-only history |
| Accountability | Reports, six suspicious-listing rules, suspension/restoration, audit log |
| Employer insight | Funnel, response times, per-post statistics, matching and geographic distribution, CSV export |
| Skills development | Skills-gap analysis, demand evidence, simulation against the matching engine, career guidance |
| Security | JWT authentication, role separation, ownership enforcement, rate limiting, validation, hardening headers |

---

## 3. Scope and delimitations

**In scope.** Four municipalities (Basud, Daet, Vinzons, Labo); three roles (job seeker, employer, administrator); web application usable on desktop and mobile browsers; English UI; single-region deployment.

**Out of scope by design.** Municipalities outside the four; payroll, attendance or HR functions; payments; SMS/email delivery (notifications are in-app only); résumé PDF generation; native mobile apps (a WebView shell exists in `mobile/` but is not part of the evaluated scope); multi-language UI; machine-learning ranking.

**Delimitation on data.** Geographic names, codes and boundaries come from the PSA/PSGC barangay dataset shipped with the system (`backend/src/main/resources/geo/barangays.geojson`). The system does not survey boundaries itself.

---

## 4. Users and roles

| Role | What they can do | What they cannot do |
| --- | --- | --- |
| **Job seeker** | Register, maintain profile and skills, search/filter/map jobs, see match scores and explanations, save jobs, apply (once per post), track status and interviews, receive notifications, withdraw, read skills-gap and career guidance | Access employer or admin functions; see another seeker's applications |
| **Employer** | Register a business, request verification, create/edit posts, submit for review, read applicants ranked by match, shortlist/interview/accept/reject, analytics and CSV export | Publish a post without approval, verify itself, or read another employer's posts, applicants or analytics |
| **Administrator** | Verify employers, approve/request changes/reject posts, handle reports, review suspicious listings, suspend/restore accounts, read audit logs and dashboard metrics | Suspend itself or the last active administrator; alter audit history |

Role is carried in the JWT and re-checked on every request; the account's active state is re-checked too, so a suspension ends sessions that are already open.

---

## 5. Functional modules

**Public.** Landing page, registration (seeker/employer), sign-in, job search and job detail without an account, health endpoint.

**Job seeker.** Dashboard, profile (skills, education, experience, location, preferences, photo), job search with filters (keyword, municipality, barangay, category, employment type, salary, distance, match band), job map, job detail with match breakdown, saved jobs, applications list, application timeline, notifications inbox, skills-gap and career guidance, reporting a listing.

**Employer.** Dashboard metrics, company profile, job list with moderation status, post/edit job (barangay + municipality selection, salary range, skills, requirements), applicant list with match scores, applicant profile, status updates, interview scheduling/rescheduling/cancellation, analytics (nine sections) and CSV export, notifications.

**Administrator.** Dashboard, employer verification queue, job moderation, user management with suspension/restoration, reports queue, suspicious listings with dismissal, geography reference, skills reference, audit logs, admin notifications.

**Cross-cutting.** Notifications generated by status changes and moderation decisions; audit entries for administrative actions; server-side validation on every write; rate limiting on sign-in, registration, report submission and job applications.

---

## 6. System architecture

```
Browser (HTML + CSS + vanilla JS, Leaflet map)
        │  JSON over HTTPS, Bearer JWT
        ▼
Spring Boot 3.4.2 application (Java 21)
  ├─ Security filter chain: rate-limit filter → JWT authentication filter → role rules
  ├─ Controllers (18)          REST endpoints, 120 mapped routes
  ├─ Services                  matching, applications, moderation, analytics, skills gap, notifications
  ├─ Repositories (Spring Data JPA)
  └─ Flyway migrations V1–V9   schema owned by migrations, ddl-auto=validate
        │  JDBC (pooled)
        ▼
PostgreSQL (managed)  — 17 tables, profile images stored as bytea
```

**Request flow.** A request passes the rate-limit filter (fixed window, per client address and action), then the JWT filter (validates the token, reloads the account, refuses suspended users), then Spring Security's role rules, then the controller. Controllers return DTOs; entities are never serialized directly, so password hashes and internal fields cannot leak.

**Why a server-rendered-ish front end?** The pages are static HTML with vanilla JavaScript calling the REST API. There is no build step and no framework runtime, which keeps the deliverable inspectable and deployable as a single Docker image that serves both the API and the pages.

---

## 7. Technology stack

| Layer | Technology | Version |
| --- | --- | --- |
| Language | Java | 21 |
| Framework | Spring Boot (Web, Data JPA, Security, Validation) | 3.4.2 |
| Database | PostgreSQL (managed) | current provider version |
| Migrations | Flyway (`flyway-database-postgresql`) | bundled with Boot 3.4.2 |
| Auth | JWT (HS256), BCrypt password hashing, stateless sessions | — |
| API docs | springdoc-openapi | 2.8.0 |
| Front end | HTML5, CSS3, vanilla JavaScript (ES5-compatible modules) | — |
| Map | Leaflet + Leaflet.markercluster | 1.9.4 / 1.5.3 |
| Build | Maven (wrapper-free, offline-capable) | 3.9.9 |
| Packaging | Docker image serving API + static pages | — |
| Hosting | Render (Docker web service) | free tier |
| Testing | JUnit 5, Spring Boot Test, MockMvc; Python HTTP suites in `tools/` | — |

**Code size (counted in this audit):** 146 Java source files, 12,577 lines of application code, 1,146 lines of tests, 18 controllers, 120 mapped endpoints, 9 database migrations, 17 tables, 15 JPA entities.

---

## 8. Database design

Seventeen tables, created and evolved only by Flyway migrations (`V1` … `V9`); the application validates the schema at start-up and cannot alter it (`ddl-auto: validate`, `flyway.clean-disabled: true`).

| Group | Tables |
| --- | --- |
| Identity | `users`, `job_seeker_profiles`, `employer_profiles` |
| Jobs | `job_posts`, `saved_jobs` |
| Applications | `job_applications`, `application_status_history`, `application_interviews` |
| Communication | `notifications`, `activity_log` |
| Skills development | `learning_resources`, `skill_learning_interests` |
| Moderation | `reports`, `suspicious_listings`, `audit_logs` |
| Platform | `uploaded_files`, `rate_limit_counters` |

Key relationships: `users` 1 : 0..1 to each profile (cascade); `employer_profiles` 1 : N `job_posts`; `job_posts` 1 : N `job_applications`; `job_seeker_profiles` 1 : N `job_applications`; `job_applications` 1 : N `application_status_history`; `job_applications` 1 : N `application_interviews`. Uniqueness on `(job_post_id, job_seeker_id)` prevents duplicate applications.

Migration history: V1 base schema, V2 uploads moved into the database, V3 seed accounts, V4 job coordinates, V5 matching indexes, V6 application tracking and notifications, V7 skills gap and learning resources, V8 admin moderation, V9 rate-limit counters.

Full column-level reference: `docs/final-release/DATABASE_DOCUMENTATION.md`.

---

## 9. Matching engine

### 9.1 Weights (verified in `MatchingEngine.java`, lines 43–47)

| Criterion | Weight | How the criterion score is computed |
| --- | --- | --- |
| Skills | **40 %** | matched required skills ÷ required skills × 100 |
| Location | **25 %** | same barangay 100 · same municipality 80 · bordering municipality 50 · otherwise 10 |
| Employment type | **15 %** | exact preference 100 · related type 60 · unrelated 0 |
| Experience | **10 %** | meets the stated minimum 100 · none required 100 · partial = recorded ÷ required × 100 |
| Salary | **10 %** | offered range meets expectation 100 · overlap 50–100 · below expectation 0 |

### 9.2 Formula

```
applicable weight = Σ weights of criteria that can be judged
earned points     = Σ (weight × criterion score / 100)

overall % = earned points ÷ applicable weight × 100     (clamped 0–100, one decimal)
```

A criterion with missing data is removed from **both** the numerator and the denominator. Missing data therefore cannot punish the seeker with a zero, nor reward them with free points. The response carries `applicableWeight`, `earnedPoints` and `complete`, so the interface can state that a score rests on a subset.

Bands: **≥ 90** Excellent · **≥ 75** Strong · **≥ 60** Good · **≥ 40** Partial · below 40 Low.

### 9.3 Worked examples (hand-calculated, then executed)

| Case | Calculation | Result |
| --- | --- | --- |
| Everything matches | 40 + 20 + 15 + 10 + 10 of 100 | **95.0**, Excellent |
| 2 of 3 skills | 26.7 + 20 + 15 + 10 + 10 | **81.7**, Strong |
| 0 of 3 skills | 0 + 20 + 15 + 10 + 10 | **55.0**, Partial |
| Same barangay | 40 + 25 + 15 + 10 + 10 | **100.0**, Excellent |
| Bordering municipality | 40 + 12.5 + 15 + 10 + 10 | **87.5**, Strong |
| No skills listed by the employer | skills dropped: 55 of 60 | **91.7**, Excellent, `complete=false` |
| No salary and no employment preference | 70 of 75 | **93.3**, Excellent, `complete=false` |

Twelve independent cases were computed by hand and all twelve matched the engine's output; the same inputs scored identically on 50 freshly constructed engine instances (no state, no randomness).

### 9.4 What it is not

No training data, no model file, no inference service, no embeddings, no natural-language understanding, no popularity weighting, no education or certification term in the score. Education and certifications are recorded and displayed but do not add points — the documentation says so explicitly.

---

## 10. Geographic subsystem

- **125 barangays** in `barangays.geojson`: **Basud 29, Daet 25, Labo 52, Vinzons 19** (counted in this audit; exact match with the documented figures).
- Each feature carries `code`, `name`, `municipalityCode`, `municipality` and polygon geometry, following the PSA/PSGC coding pattern (e.g. `0501601001` = Angas, Basud), which is the provenance of the names and boundaries.
- Forms restrict barangay choice to the selected municipality; a job pin is plotted only when latitude and longitude are present.
- Location scoring is **adjacency-aware**: Daet–Basud, Daet–Vinzons, Daet–Labo, Labo–Vinzons and Labo–Basud are treated as bordering; Basud–Vinzons is not.
- Map: Leaflet 1.9.4 with marker clustering; tile configuration is supplied by the backend (`/api/geo/map-config`) so no page hard-codes a tile provider.
- Radius and "near me" search use the stored coordinates; jobs without coordinates are excluded from distance results and say so.

---

## 11. Application lifecycle

| From | Allowed next states |
| --- | --- |
| `SUBMITTED` | `REVIEWING`, `SHORTLISTED`, `REJECTED` |
| `REVIEWING` | `SUBMITTED`, `SHORTLISTED`, `INTERVIEW`, `REJECTED` |
| `SHORTLISTED` | `REVIEWING`, `INTERVIEW`, `ACCEPTED`, `REJECTED` |
| `INTERVIEW` | `SHORTLISTED`, `ACCEPTED`, `REJECTED` |
| `ACCEPTED` | `SHORTLISTED`, `REJECTED` |
| `REJECTED` | `REVIEWING`, `SHORTLISTED` |
| `WITHDRAWN` | — final state (set by the seeker only) |

- Every transition writes an `application_status_history` row with the actor, role and reason; that record is the timeline the seeker and employer both read.
- Interviews are separate records (`application_interviews`) with schedule, mode, location and notes, and can be rescheduled or cancelled.
- Status changes generate notifications for the other side.
- A withdrawn application is terminal: no further employer transition is accepted, and `canManage` returns false.
- One application per seeker per post (database uniqueness), which also blocks re-applying after withdrawal.

---

## 12. Security design

| Control | Implementation | Evidence |
| --- | --- | --- |
| Authentication | E-mail + BCrypt password, JWT (HS256) bearer token, stateless sessions | code; Phase 7 suite |
| Administrator sign-in | Name-only shortcut requires `JSM_ADMIN_ACCESS_CODE`; refused when the variable is unset | Phase 7 commit `81e31f0`; local check |
| Authorisation | Role rules per route (`hasRole('ADMIN')`, employer/seeker ownership checks) | `ModerationAuthorizationTest`; Phase 7 suite |
| Ownership (IDOR) | Employer A cannot read or modify B's posts, applicants or analytics; seekers see only their own applications | Phase 7 suite, cross-tenant block |
| Suspended accounts | Login refused **and** existing tokens rejected on the next request | Phase 7 suite |
| Token expiry | Expired or tampered token → 401 | Phase 7 suite |
| Rate limiting | PostgreSQL-backed fixed window per action and per trusted client address (last `X-Forwarded-For` entry, which the proxy writes) | `RateLimiterTest` (8 tests); 21st local attempt → 429 with `Retry-After` |
| Input validation | Bean Validation on DTOs, page-size caps, 400 instead of 500 on malformed input | production: malformed JSON → 400 |
| SQL injection | Bound parameters through JPA/SQL parameters; no string-concatenated queries | Phase 7 suite |
| XSS | Escaped output; analytics table escaped explicitly; CSV export guards formula injection | Phase 7 fix |
| CSRF | Disabled deliberately — stateless bearer tokens, no cookie-based session | `SecurityConfig` |
| Headers | HSTS, `X-Frame-Options: DENY`, `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`, `no-store` cache control | observed in production |
| Error handling | Global handler maps errors to 400/401/403/404/405/409 with a message, never a stack trace | Phase 7 suite; production 400 |
| Secret hygiene | No secret in the repository or in any document; configuration comes from environment variables | secret scan, Phase 8 |

Known, disclosed weaknesses: public Swagger UI and `/api-docs`, no Content-Security-Policy, JWT stored in `localStorage`, no per-account throttling, no independent penetration test.

---

## 13. Testing and acceptance evidence

**Automated tests — executed on 2026-11-26 in this audit:** `mvn test` against PostgreSQL with all nine migrations and `ddl-auto=validate` → **59 tests, 0 failures**.

| Class | Tests | Proves |
| --- | --- | --- |
| `MatchingEngineTest` | 25 | weights, each criterion, missing-data renormalisation, bands, explanation text, skill parsing |
| `SuspiciousListingServiceTest` | 12 | each detection rule, dismissal stops re-flagging |
| `RateLimiterTest` | 8 | limit, per-address/action isolation, window reset, disable switch, fail-open, spoof-resistant key |
| `ReportServiceTest` | 7 | report lifecycle and statistics |
| `ModerationAuthorizationTest` | 6 | admin-only routes, read-only audit logs, no employer self-verification |
| `JobSkillsMatchingSystemApplicationTests` | 1 | full context boot, Flyway to V9, schema validation |

**Engine acceptance:** 12 hand-calculated cases + determinism over 50 engine instances — all pass (see §9).

**Scripted HTTP suites (earlier phases, local instance):** `tools/phase7_security_check.py` 50/50, `tools/phase6_moderation_check.py` 92/92, `tools/phase6_job_moderation_check.py` 36/36 — 178 assertions, overlapping, not summed as unique tests.

**Production, read-only (2026-11-26):** health `{"status":"UP","database":"UP"}`; 27 pages return 200; anonymous admin and application endpoints 401; `/actuator/env` 401; malformed JSON 400; security headers present; rate-limit headers `limit 20`, `remaining 18` decreasing; deploy `dep-dc45f4m7bikc738719ig` on commit `b207ee0` live.

**Not verified:** authenticated production workflows for the three roles, admin mutations in production, analytics figures compared against production rows, responsive re-measurement, keyboard/contrast accessibility, backup restore, and load behaviour. Details and the honest level of every claim are in `docs/final-release/PHASE_9_ACCEPTANCE_TEST_REPORT.md`.

---

## 14. Deployment and operations

| Item | Value |
| --- | --- |
| Host | Render web service `ENUVEX`, Docker, free plan, Singapore region, one instance |
| Auto-deploy | Every push to `main` builds and redeploys |
| Live commit | `b207ee0` (`dep-dc45f4m7bikc738719ig`, live 2026-11-26T16:03:46Z) |
| Database | Managed PostgreSQL; connection supplied by environment variables |
| Configuration | `JSM_ADMIN_ACCESS_CODE`, `JSM_RATE_LIMIT_*`, `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET` (names only — values live in the platform, never in the repository) |
| Migrations | Flyway runs at start-up; a failed migration stops the deployment rather than serving a broken schema |
| Health | `GET /api/health` reports application **and** database readiness |
| Cold start | The free instance sleeps when idle; the first request on 2026-11-26 took **63.5 s**, later requests ≈ 0.5 s |
| Logging | Platform log stream; administrative actions also recorded in `audit_logs` |
| Backup | Provider automated backups; a restore rehearsal is still an operator task |

**Local run (for a fallback demo):** `cd backend && mvn spring-boot:run` with the environment variables above and a reachable PostgreSQL; Flyway creates the schema. Maven package produces a runnable jar; the Dockerfile at the repository root builds the same image the host runs.

---

## 15. Documented limitations

1. Free hosting tier: instance sleeps, single instance, no uptime guarantee.
2. No CSP; JWT in `localStorage`.
3. Swagger UI and `/api-docs` publicly reachable.
4. No per-account throttling (throttling is per address and action).
5. Some administrator lists are paginated in the browser rather than on the server.
6. Seeded administrator password not yet rotated in the deployed environment.
7. No rehearsed backup restore, no load test, no independent security audit, no user-acceptance study — the project therefore claims no performance benchmark and no satisfaction score.
8. Notifications are in-app only (no e-mail or SMS).
9. Coverage is limited to four municipalities by design.

---

## 16. Demonstration plan (condensed)

1. **Warm the host 2–3 minutes early** — the free tier sleeps (63.5 s measured).
2. Landing page → problem, coverage, two user journeys.
3. Seeker sign-in → search → filter by municipality and employment type.
4. Open a job → read the match percentage and its per-criterion sentences → explain the weights.
5. Map → barangay boundaries, pins, near-me.
6. Skills gap → a missing skill, its local demand, the simulation result.
7. Apply → confirmation → tracker entry.
8. Employer → applicants ranked by match → shortlist → schedule interview (visible status change + notification).
9. Employer analytics → funnel and CSV export.
10. Administrator → dashboard, verification queue, audit log.
11. Close on architecture and the deterministic-engine slide.

Fallbacks: screenshots in `docs/presentation/screenshots/`, a screen recording of the application flow, the local instance, and the deck exported to PDF. Full step-by-step guide: `docs/presentation/LIVE_DEMO_GUIDE.md`.

---

## 17. Glossary

| Term | Meaning |
| --- | --- |
| Barangay | Smallest administrative division in the Philippines |
| Municipality | Town; the system covers Basud, Daet, Vinzons, Labo |
| PSGC | Philippine Standard Geographic Code |
| Match percentage | Weighted score 0–100 for one seeker–job pair |
| Renormalisation | Removing an unjudgeable criterion from both numerator and denominator |
| IDOR | Insecure Direct Object Reference — accessing another user's record by guessing its id |
| JWT | JSON Web Token, the bearer token used for authentication |
| Moderation status | `PENDING_REVIEW`, `APPROVED`, `CHANGES_REQUESTED`, `REJECTED` |
| Flyway | Migration tool that owns the database schema |

---

## 18. Where the detailed material lives

| Topic | File |
| --- | --- |
| Project overview, problem, objectives | `docs/final-release/PROJECT_OVERVIEW.md` |
| Architecture and request flow | `docs/final-release/SYSTEM_ARCHITECTURE.md` |
| Database, table by table | `docs/final-release/DATABASE_DOCUMENTATION.md` |
| Matching engine, full rules | `docs/final-release/MATCHING_ENGINE.md` |
| API reference (120 routes) | `docs/final-release/API_DOCUMENTATION.md` |
| Install and deploy | `docs/final-release/INSTALLATION_AND_DEPLOYMENT.md` |
| Test results, what was and was not run | `docs/final-release/TESTING_AND_QA_REPORT.md` |
| Security controls and limitations | `docs/final-release/SECURITY_AND_LIMITATIONS.md` |
| Release checklist | `docs/final-release/RELEASE_CHECKLIST.md` |
| Executive summary | `docs/final-release/FINAL_PROJECT_SUMMARY.md` |
| Phase 9 acceptance audit | `docs/final-release/PHASE_9_ACCEPTANCE_TEST_REPORT.md` |
| User manuals | `docs/user-manuals/USER_MANUAL_JOB_SEEKER.md`, `…_EMPLOYER.md`, `…_ADMIN.md` |
| Slides, script, Q&A, demo guide | `docs/presentation/JSM_Final_Defense.pptx`, `PRESENTER_SCRIPT.md`, `DEFENSE_QUESTIONS_AND_ANSWERS.md`, `LIVE_DEMO_GUIDE.md` |

---

## 19. Acceptance statement

As of the audit on 2026-11-26 the system builds cleanly, passes its complete automated suite (**59/59**), matches its documented matching algorithm on twelve independently hand-calculated cases, carries the exact geographic dataset it claims (125 barangays, 29/25/52/19), and is live in production on the audited commit with correct health reporting, hardening headers, anonymous-access refusals and rate-limit headers.

No product defect was found in that audit. The remaining gaps are operational and disclosed: authenticated production workflows were not re-exercised, and the limitations in §15 stand. Within that stated scope, **the system is ready for academic presentation, demonstration and final acceptance**.

---

*Prepared from the running system and its source. Figures are reproducible: `cd backend && mvn test`, the matching cases in §9, and the read-only checks listed in §13.*
