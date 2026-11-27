# JSM — Complete System Documentation and Code Explanation

**System:** Job & Skills Matching (JSM), deployed under the ENUVEX product name
**Live system:** https://enuvex.onrender.com
**Repository:** `Hacking336/ENUVEX`, branch `main`, audited commit `b207ee0`
**Document basis:** source code, database migrations, the automated test suite and the read-only production audit in `docs/final-release/PHASE_9_ACCEPTANCE_TEST_REPORT.md` (2026-11-26). Every number below was produced by the system, not estimated.
**Contents:** Part I — system documentation (sections 1–19) · Part II — data structures and algorithms (sections 20–23) · Part III — code explanation (sections 24–41).
**Fields to complete by the group:** panel/course/school, group member names, adviser, date of defense. They are deliberately left blank here — this document does not invent them.

---

## How this document is organised

This is the single reference document for the project. It replaces the separate system documentation and code explanation with one continuous text, and adds the two chapters a panel usually asks for after the demo: **the data structures** the system is built on and **the algorithms** that run on them.

| Part | Sections | What it covers |
| --- | --- | --- |
| I — System documentation | 1–19 | the problem, the users, the architecture, the database, the matching rules, security, testing, deployment, limitations, demo plan, acceptance statement |
| II — Data structures and algorithms | 20–23 | every persistent and in-memory structure, every algorithm with its pseudocode and cost, and what was deliberately left out |
| III — Code explanation | 24–41 | a guided walk through the source: one request end to end, the services, the deep dives, the front end, the tests, and a fifteen-minute reading order |

---
**PART I — SYSTEM DOCUMENTATION**


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

**PART II — DATA STRUCTURES AND ALGORITHMS**


This part answers the two questions a panel asks about the code behind the demo: **what data structures does the system actually use**, and **what algorithms run on them**. Every structure and every algorithm below was read from the source at commit `b207ee0`; nothing here is a textbook inventory of things the system might have used.

## 20. Data structures — overview

JSM uses two families of structure, and the choice in each case is the smallest one that supports the operation the code actually performs: a **map** where the code looks a value up by its official code, a **set** where the code asks "does the seeker already have this skill", an **adjacency list** where the code asks "is this municipality next to mine", and a **table** where the answer has to survive a restart.

| Structure | Kind | Where it lives | What it answers |
| --- | --- | --- | --- |
| 17 relational tables | persistent | PostgreSQL, mapped by 15 JPA entities | everything that must survive a restart |
| 38 B-tree / unique indexes | persistent | the Flyway migrations | "find the rows for this user / status / municipality" without a scan |
| `EnumMap<Municipality, List<Municipality>>` `NEARBY` | adjacency list | static field, `MatchingEngine` | "does this municipality border mine?" in one array access |
| `EnumMap<EmploymentType, List<EmploymentType>>` `COMPATIBLE_TYPES` | adjacency list | static field, `MatchingEngine` | "is this job type related to my preference?" |
| `EnumMap<ApplicationStatus, Set<ApplicationStatus>>` `TRANSITIONS` | adjacency list + bitset | static field, `ApplicationService` | "may the application move from here to there?" |
| `Set<String>` `NO_EXPERIENCE_TOKENS` | hash set | static field, `MatchingEngine` | "does this phrase mean zero years?" |
| `LinkedHashMap<String, MunicipalityFeature>` `municipalitiesByCode` | hash map, insertion-ordered | `GeoService`, built at start-up | PSGC code → municipality + polygon |
| `EnumMap<Municipality, MunicipalityFeature>` `municipalitiesByEnum` | array-backed map | `GeoService` | municipality enum → polygon, no hashing |
| `LinkedHashMap<String, BarangayFeature>` `barangaysByCode` | hash map | `GeoService` | PSGC code → barangay, official name |
| `LinkedHashMap<String, List<BarangayFeature>>` `barangaysByMunicipality` | hash map of lists | `GeoService` | "all 52 barangays of Labo" in one lookup |
| `List<List<double[][]>>` | jagged primitive array | `GeoService` | polygon → rings → `[lng, lat]` pairs |
| `HashSet<String>` of normalised skills | hash set | per call, `MatchingEngine` | "is this required skill in the profile?" |
| `List<MatchCriterionDto>` | ordered list, fixed 5 | per call, `MatchingEngine` | the five criteria, in the order the UI prints them |
| `record ExperienceRequirement(double minYears, Double maxYears)` | immutable tuple | per call | a parsed experience requirement |
| `record Decision(boolean, int, int, long)` | immutable tuple | per request | the rate limiter's answer |
| `record ResolvedLocation(String, String, String)` | immutable tuple | per write | the official codes a pin resolved to |
| `Specification<JobPost>` | predicate tree | per search | the ANDed filters of a job search |
| `Page<JobResponse>` / `PageRequest` | page + sort descriptor | per search | one page of results and how it was cut |
| `LinkedHashMap<String, Object>` | ordered map | analytics/skills-gap responses | JSON whose key order is stable for the reader |

Two things are deliberately **absent**: there is no cache layer (no Redis, no in-process cache) and no in-memory index of jobs. Every job query goes to PostgreSQL, which is why a second instance would need no coordination and why a restart costs nothing but the 63.5 s cold start.

## 21. Data structures, layer by layer

### 21.1 Persistent structures — the seventeen tables

Fifteen entities map onto seventeen tables; every table has a surrogate `id BIGINT` primary key except `rate_limit_counters`, whose key is the bucket string itself (two tables are written by native SQL rather than an entity: `rate_limit_counters` and the `activity_log` feed rows). Enumerations are stored as **text**, never as ordinals, so a row stays readable in `psql` and reordering a Java enum can never change the meaning of stored data.

| Table | Keys and constraints | Role |
| --- | --- | --- |
| `users` | `email` UNIQUE | one account per person; BCrypt hash, `user_type`, `is_active` |
| `job_seeker_profiles` | `user_id` UNIQUE → `users` | skills, education, work history, location, preferences |
| `employer_profiles` | `user_id` UNIQUE → `users` | business identity and `verification_status` |
| `job_posts` | `employer_id` → `employer_profiles` | the vacancy, its moderation state, its pin |
| `saved_jobs` | UNIQUE (`job_seeker_id`, `job_post_id`) | the seeker's shortlist |
| `job_applications` | `unique_application` UNIQUE (`job_seeker_id`, `job_post_id`) | one application per seeker per post |
| `application_status_history` | `application_id` → `job_applications` | append-only timeline: previous → new, actor, reason |
| `application_interviews` | `application_id` **UNIQUE** → `job_applications` | at most one interview per application |
| `notifications` | UNIQUE (`user_id`, `dedupe_key`) | in-app inbox, idempotent per event |
| `activity_log` | `user_id` → `users` | the user-facing activity feed |
| `learning_resources` | — | learning material linked to a skill key |
| `skill_learning_interests` | UNIQUE (`seeker_profile_id`, `skill_key`) | "I want to learn this" |
| `reports` | `reporter_id` → `users` | user reports against a job or an employer |
| `suspicious_listings` | `job_post_id` → `job_posts` | one row per fired rule, with a dismissal flag |
| `audit_logs` | `actor_id` → `users` | append-only record of every admin decision |
| `uploaded_files` | owner → `users` | profile images stored as `bytea` inside the database |
| `rate_limit_counters` | primary key is `bucket_key` itself | one counter row per action and client address |

The `rate_limit_counters` primary key deserves a sentence: the key is the **bucket itself**, a string such as `jsm:rate:login:203.0.113.7`. Making the bucket the key is what lets the limiter increment it with a single `INSERT … ON CONFLICT (bucket_key) DO UPDATE`, so two simultaneous requests cannot both read a stale count.

### 21.2 Enumerations — closed sets, stored as text

Thirteen enums encode the values that must never be free text. A closed set is a data structure too: it makes an invalid status unrepresentable rather than merely discouraged.

| Enum | Values | Where it is used |
| --- | --- | --- |
| `UserType` | `JOB_SEEKER`, `EMPLOYER`, `ADMIN` | the account's role, carried in the JWT and re-checked per request |
| `ApplicationStatus` | `SUBMITTED`, `REVIEWING`, `SHORTLISTED`, `INTERVIEW`, `ACCEPTED`, `REJECTED`, `WITHDRAWN` | the hiring pipeline and its state machine |
| `ModerationStatus` | `PENDING_REVIEW`, `APPROVED`, `CHANGES_REQUESTED`, `REJECTED` | whether a vacancy may be published |
| `VerificationStatus` | `UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED`, `NEEDS_INFORMATION` | employer verification |
| `EmploymentType` | `PART_TIME`, `FULL_TIME`, `CONTRACT`, `TEMPORARY` | the 15 % matching criterion |
| `Municipality` | `BASUD`, `DAET`, `VINZONS`, `LABO` | the coverage area and the 25 % criterion |
| `EducationLevel` | `NO_FORMAL_EDUCATION` … `POST_GRADUATE` (six levels) | profile and post requirement (displayed, not scored) |
| `WorkSchedule` | `MORNING`, `AFTERNOON`, `EVENING`, `NIGHT`, `WEEKEND`, `FLEXIBLE` | shift information |
| `Availability` | `IMMEDIATELY` … `MORE_THAN_3_MONTHS` (six values) | when the seeker can start |
| `JobCategory` | nineteen categories (retail, sales, healthcare, construction, IT, …) | classification, search filter, career grouping |
| `BusinessType` | nineteen industry types | employer profile |
| `ReportCategory` | `FRAUD_OR_SCAM`, `MISLEADING_INFORMATION`, `FAKE_EMPLOYER`, `DUPLICATE_LISTING`, `WRONG_LOCATION`, `INAPPROPRIATE_CONTENT`, `OTHER` | why a listing was reported |
| `ReportStatus` | `OPEN`, `UNDER_REVIEW`, `RESOLVED`, `DISMISSED` | the report queue |

### 21.3 Start-up structures — the geographic index

`GeoService.load()` runs once, before the first request is accepted, and turns two GeoJSON files into four lookup structures. The sizes below were counted from the shipped files.

| Structure | Entries | Shape |
| --- | --- | --- |
| `municipalitiesByCode` | 4 | `LinkedHashMap` keyed by PSGC code (`0501601000` …) |
| `municipalitiesByEnum` | 4 | `EnumMap` keyed by `Municipality`, so a lookup is an array index |
| `barangaysByCode` | 125 | `LinkedHashMap` keyed by PSGC code |
| `barangaysByMunicipality` | 4 keys | `LinkedHashMap<String, List<BarangayFeature>>` — Basud 29, Daet 25, Labo 52, Vinzons 19 |
| municipality geometry | 820 vertices | `List<List<double[][]>>` — 4 features, 205 vertices on average |
| barangay geometry | 1,635 vertices | same shape — 125 features, 13.1 vertices on average |

```
GeoJSON file                    in-memory structure                question answered
─────────────────────────────   ────────────────────────────────   ───────────────────────────────
municipalities.geojson  ──────► byCode:  "0501603000" → DAET      "which municipality is this?"
                                byEnum:  DAET → feature           "where is its polygon?"
                                polygons: List<List<double[][]>>  "is this pin inside it?"

barangays.geojson       ──────► byCode:  "0501603011" → barangay "what is its official name?"
                                byMuni:  DAET → [25 barangays]    "list its barangays"
                                polygons: List<List<double[][]>>  "which barangay holds this pin?"
```

A `LinkedHashMap` is used where the order is shown to a human (municipalities appear in PSGC order, not hash order) and an `EnumMap` where the key is an enum (no hashing, no allocation, and it cannot hold a key outside the enum). Polygon coordinates are kept as `double[][2]` rather than a list of point objects: 2,455 vertices become 2,455 primitive doubles instead of 2,455 objects, and a point-in-polygon test walks a flat array.

### 21.4 Per-request structures

| Structure | Built by | Life | Why this shape |
| --- | --- | --- | --- |
| `List<MatchCriterionDto>` (5 entries) | `MatchingEngine.calculateMatch` | one request | a fixed, ordered list: the UI prints the criteria in the same order every time |
| `HashSet<String>` of normalised skills | `normalizedSkills()` | one scoring call | membership tests instead of a nested loop over the profile |
| `Set<String>` of required-skill keys (inside `splitSkills`) | `splitSkills()` | one parse | removes duplicates while keeping the employer's original spelling |
| `ExperienceRequirement` record | `parseRequirement()` | one criterion | two values that belong together; a tuple prevents them drifting apart |
| `Decision` record | `RateLimiter.check()` | one request | the filter needs four numbers and cannot mutate them later |
| `ResolvedLocation` record | `GeoService.validate()` | one write | returns the **official** code and name, never the client's spelling |
| `Specification<JobPost>` | `JobService.buildSpecification()` | one search | composable predicates, so filters can be added without a query string |
| `PageRequest` / `Page<JobResponse>` | `searchJobs()` | one search | page number, size and sort travel together with the rows |
| `List<Candidate>` (private record) | `SkillsGapService.buildPriorities()` | one request | a scratch row per missing skill before weights are applied |

### 21.5 Persistent indexes — where the database does the searching

The nine migrations contain **38 index-creation statements** (several repeat an earlier index with `IF NOT EXISTS`, so the live count is smaller than the statement count). Grouped by the question they answer:

| Purpose | Indexes |
| --- | --- |
| Job search and listing | `job_posts(is_active, created_at DESC)`, `(is_active, municipality)`, `(municipality)`, `(job_category)`, `(employment_type)`, `(salary_max)`, `(employer_id)`, `(moderation_status)`, `(barangay_code)`, `(municipality_code)` |
| Applications | `job_applications(job_post_id)`, `(job_seeker_id)`, `(application_status)`, `application_status_history(application_id)`, `(created_at)`, `application_interviews(application_id)` |
| Notifications | `notifications(user_id)`, `(user_id, read_at)`, `(user_id, created_at)`, UNIQUE `(user_id, dedupe_key)` |
| Profiles | `job_seeker_profiles(municipality)`, `(barangay)`, `employer_profiles(municipality)` |
| Moderation | `reports(status)`, `(reported_target_type, reported_target_id)`, `(reporter_id)`, `suspicious_listings(job_post_id)`, `(triggered_rule)` |
| Skills development | `learning_resources(skill_key)`, `(is_active)`, `skill_learning_interests(seeker_profile_id)` |
| Rate limiting | `rate_limit_counters(updated_at)` — for deleting stale windows |

The composite `(is_active, created_at DESC)` index exists because the default job list is exactly "active posts, newest first"; a single-column index on `is_active` would still have to sort. The unique indexes are not only for speed: `unique_application (job_seeker_id, job_post_id)` and `uq_notifications_user_dedupe (user_id, dedupe_key)` are the two places where a **data structure enforces a business rule** that application code alone could not guarantee under concurrency.

## 22. Algorithms

Each algorithm below is described by its rule, its pseudocode as the code actually implements it, and its cost. Complexity is stated in terms of the real inputs, and the real input sizes are the ones the system ships with (4 municipalities, 125 barangays, 2,455 polygon vertices).

### 22.1 The matching algorithm — weighted score with renormalisation

The core of the system. It is a **deterministic weighted sum**, not a learned model: five criteria, fixed weights, and a criterion that cannot be judged is removed from both the numerator and the denominator.

```
weights:  skills 40 · location 25 · employment type 15 · experience 10 · salary 10

for each of the five criteria:
    if the data needed to judge it exists:
        applicable += weight
        earned     += weight × criterionScore / 100
    else:
        complete = false                      # reported to the client

overall = applicable > 0 ? clamp(earned / applicable × 100) : 0
overall = round to 1 decimal
band    = ≥90 Excellent · ≥75 Strong · ≥60 Good · ≥40 Partial · otherwise Low
```

**Why renormalise.** If a missing skill list simply scored zero, an employer who disclosed nothing would cap every applicant at 60 %. Removing the weight from both sides keeps the score honest in the other direction too: a job with no listed skills cannot hand a seeker 40 free points, because those 40 points are not in the denominator either. The client receives `applicableWeight`, `earnedPoints` and `complete`, so the interface can say that a score rests on a subset.

**Cost.** Five criteria, each constant work except the skill comparison: `O(|requiredSkills| + |profileSkills|)` expected using a hash set. Memory `O(|skills|)`. No I/O, no randomness, no shared state, which is why the same inputs give the same output on any instance.

### 22.2 Skill tokenisation and normalisation

Free-text skills ("Customer Service, cooking; Driving") are turned into canonical keys before any comparison.

```
splitSkills(raw):
    split raw on commas, semicolons, newlines, bullets, pipes
    collapse inner whitespace, trim
    key = normalizeSkill(part)
    if key is new: keep part (the employer's original spelling)

normalizeSkill(raw):
    lower-case
    replace curly apostrophes with '
    collapse whitespace
    strip leading/trailing spaces, dots and hyphens

skillsMatch(required, profile):
    have = { normalizeSkill(s) for s in splitSkills(profile) }   # a set
    matched = count(r for r in required if normalizeSkill(r) in have)
    score   = matched / |required| × 100
```

The set is what makes the comparison linear: without it, each of `R` required skills would be compared with each of `S` profile skills, giving `O(R × S)`. With it, the profile is hashed once (`O(S)`) and each requirement is one lookup (`O(R)`).

### 22.3 Location tiering — a four-node adjacency list

Rather than computing a distance for the location criterion, the engine uses a **tiered lookup table** built once as an `EnumMap` of adjacency lists:

```
NEARBY = { BASUD: [DAET],
           DAET:  [BASUD, VINZONS, LABO],
           VINZONS:[DAET, LABO],
           LABO:  [DAET, VINZONS, BASUD] }

score: same municipality and same barangay → 100
       same municipality, other barangay    → 80
       job municipality ∈ NEARBY[seeker]    → 50
       otherwise                            → 10
```

This is a deliberate simplification with a stated consequence: Basud–Vinzons scores 10 even though the two are a short drive apart, because they do not share a land border. It is documented rather than hidden, and it keeps the criterion explainable in one sentence ("Daet borders your Basud").

**Cost.** `O(1)` — an array index by enum ordinal plus a scan of at most three list entries.

### 22.4 Experience parsing — a token set plus a two-number scan

```
parseRequirement(text):
    lower-case, underscores → spaces
    if text ∈ NO_EXPERIENCE_TOKENS, or contains "no experience",
       "without experience", "entry level" or "fresh grad":
           return (min 0, max null)
    first  = first number found, else return null (unavailable)
    second = second number found, if any
    if second < first: swap            # "2-3 years" → min 2, max 3
    return (min first, max second)
```

Two lookups make the common cases exact: a hash set of twelve phrases ("none", "entry level", "fresh graduate", "0 years", …) and a regular expression for `N years` / `N yrs`. The seeker's side is symmetric: an explicit "5 years" in the work history wins; otherwise any non-empty work history counts as **one** year, on the stated ground that the seeker has recorded at least one job.

Scoring: requirement zero → 100; seeker zero and requirement positive → 0; else `min(100, seekerYears / requiredYears × 100)`.

**Cost.** `O(length of the text)` for the scan; constant afterwards.

### 22.5 Salary — interval overlap

Both ranges are optional on both sides.

```
reachesMinimum     = jobMax is null or jobMax ≥ seekerMin
startsBeforeMaximum= seekerMax is null or jobMin ≤ seekerMax

if both hold:
    jobMin ≥ seekerMin            → 100
    else, jobMax known            → 50 + 50 × (jobMax − seekerMin) / (jobMax − jobMin)
    else (open-ended range)       → 60
else                              → 0
```

The overlap term is what stops the score from being a cliff: a range that only partly reaches the expectation still earns between 50 and 100, in proportion to how much of the offered range is acceptable. A missing range on either side makes the criterion unavailable rather than zero.

**Cost.** `O(1)` — four comparisons and one division.

### 22.6 Profile completeness — a weighted checklist

Six checks, each with a fixed weight that sums to 100: skills 25, work experience 20, location 20, education 15, employment type 10, expected salary 10. A check that passes adds its weight to the percentage; each failing check becomes a `{key, label, href}` item telling the seeker exactly where to fix it.

**Cost.** `O(1)`, and it never touches the database — it reads the profile object already in memory.

### 22.7 Point-in-polygon — ray casting, with holes and a tolerance band

Every employer pin is validated against the municipality polygon and the barangay polygon. The algorithm is the standard **ray-casting (crossing number)** test:

```
ringContains(ring, lng, lat):
    inside = false
    for each edge (j → i) of the closed ring:
        if (yi > lat) ≠ (yj > lat)                       # edge crosses the horizontal ray
           and lng < (xj − xi) × (lat − yi) / (yj − yi) + xi:
               inside = not inside
    return inside
```

A GeoJSON `Polygon` is a list of rings: ring 0 is the outer boundary, rings 1..n are holes. `contains()` therefore requires "inside the outer ring **and** inside no hole". `MultiPolygon` is a list of such polygons, and a point inside any of them counts.

Because the shipped boundaries are simplified to 1 % of their vertices, a real workplace can fall a few metres outside its own coastline. `insideOrNear()` therefore accepts a point within **250 m** of any ring, measured as the distance to the nearest **segment** (not vertex):

```
distanceToSegment(a, b, p):
    t = clamp( −(a·(b−a)) / |b−a|² , 0, 1 )
    return |a + t(b−a)|
```

The same tolerance appears twice more: 100 m when checking that a pin does not belong to a neighbouring barangay, and the pin/barangay agreement rule, which only raises an error when the pin is inside a **different** barangay **and** more than 100 m outside the chosen one. Boundary noise is tolerated; a genuine mistake is refused with the name of the barangay the pin actually falls in.

**Cost.** `O(V)` per ring, where `V` is the number of vertices. The shipped data makes this cheap: a municipality ring averages 205 vertices and a barangay ring 13.1. Checking which barangay contains a pin is a linear scan of the municipality's barangay list (29, 25, 52 or 19 entries), so the worst case is about 52 × 13 ≈ 676 edge tests — microseconds.

### 22.8 Distance — the haversine formula

The "near me" and radius filters use the great-circle distance on a spherical Earth of radius 6,371.0088 km:

```
a = sin²(Δlat/2) + cos(lat₁)·cos(lat₂)·sin²(Δlng/2)
d = 2 · R · atan2(√a, √(1−a))
```

**Cost.** `O(1)` per pair. With a few hundred active posts, scoring every candidate is cheaper than maintaining a spatial index, and the design says so: there is **no R-tree and no PostGIS** in the system. The seeker's own coordinates are never stored — only the distance is used.

### 22.9 Fixed-window rate limiting — one atomic upsert

Four actions are protected, each with its own limit and window: sign-in 10/min, registration 5/hour, report 10/hour, application 30/hour (all configurable).

```
now         = epoch seconds
windowStart = now − (now mod windowSeconds)
bucketKey   = "jsm:rate:" + action + ":" + clientKey

hits = UPSERT rate_limit_counters (bucket_key, window_start, hits, updated_at)
       VALUES (?, ?, 1, now)
       ON CONFLICT (bucket_key) DO UPDATE
         SET hits = CASE WHEN window_start = EXCLUDED.window_start
                         THEN hits + 1 ELSE 1 END,
             window_start = EXCLUDED.window_start,
             updated_at   = EXCLUDED.updated_at
       RETURNING hits

allowed     = hits ≤ limit
retryAfter  = windowStart + windowSeconds − now
```

Three properties come from the data structure rather than from application logic:

1. **Atomicity.** The counter is incremented inside the database, so two simultaneous requests cannot both read `hits = 9` and both be allowed as the 10th.
2. **Rollover.** The `CASE` resets the count when the window changes, which is what makes it a **fixed** window rather than a permanent counter.
3. **Fail-open.** If the counter table cannot be read (`DataAccessException`), the request is **allowed** and a warning is logged. A database hiccup must not lock every user out of sign-in; the trade-off is stated in the security section rather than hidden.

The client key is a hash of the **last** entry of `X-Forwarded-For` — the address the trusted proxy wrote. Reading the first entry would let a client choose its own bucket by sending a header, which defeats the limiter entirely.

**Cost.** One indexed upsert (`O(log N)` on the primary key) per protected request, and one row per active bucket. Stale windows are removed by a scan on `updated_at`.

### 22.10 The application state machine — a transition table

Instead of a chain of `if` statements, the legal moves are declared once as data:

```
TRANSITIONS = EnumMap<ApplicationStatus, Set<ApplicationStatus>>
  SUBMITTED  → { REVIEWING, SHORTLISTED, REJECTED }
  REVIEWING  → { SUBMITTED, SHORTLISTED, INTERVIEW, REJECTED }
  SHORTLISTED→ { REVIEWING, INTERVIEW, ACCEPTED, REJECTED }
  INTERVIEW  → { SHORTLISTED, ACCEPTED, REJECTED }
  ACCEPTED   → { SHORTLISTED, REJECTED }
  REJECTED   → { REVIEWING, SHORTLISTED }
  WITHDRAWN  → { }                        # terminal, seeker-initiated only
```

`updateStatus()` then checks, in this order: the application exists → the caller owns the job → the new state differs from the current one → the move is in the table → the move is not an employer attempting `WITHDRAWN`. Only then does it write the new status, append a `application_status_history` row and notify the other side.

Using an `EnumMap` of `EnumSet`s makes the membership test a bit masked against a long, i.e. `O(1)` with no allocation, and — more importantly — makes the whole lifecycle readable in twelve lines instead of being scattered across the service.

**Cost.** `O(1)`. The history append is one insert; the transaction is the only real cost.

### 22.11 Dynamic job search — a predicate tree, then ranking

Filters arrive optional, so the query cannot be a fixed string. `buildSpecification()` composes a JPA `Specification` (a tree of predicates) and always ANDs two non-negotiable ones: `is_active = true` and `moderation_status = APPROVED`.

```
spec = TRUE
for each supplied filter: spec = spec AND predicate(filter)
spec = spec AND isActive AND moderationStatus = APPROVED

if sorting by match, or a match band was requested:
    rows   = findAll(spec, page 0, size 1000, sort createdAt DESC)   # whole filtered set
    scored = rows.map(job → job with match %)
    keep   = band filter applied
    sort   = by score DESC
    return page(slice of scored)
else:
    return findAll(spec, page, size, chosen sort)                    # the database pages
```

This is a deliberate correctness-over-speed decision: ranking and band filtering need the whole filtered set, so they are done on the server **before** the page is cut — otherwise page 2 could contain a better match than page 1. The cap of 1,000 rows bounds the cost of one request. The keyword filter is a `LIKE '%term%'`, which cannot use a B-tree; it is applied on top of the indexed filters (active, municipality, category), which is why the composite indexes matter.

**Cost.** `O(k log k)` for the ranking path, where `k ≤ 1000` is the filtered set; `O(log N + pageSize)` for the ordinary paged path, dominated by the database's index scan.

### 22.12 Duplicate and suspicious listing detection — normalised comparison

Six rules run when a post is submitted or edited. Each compares the new post with the employer's other posts using `normalize()`, which lower-cases, replaces every non-alphanumeric character with a space, collapses whitespace and trims:

```
normalize("Hiring  Staff!! (Urgent)") → "hiring staff urgent"
```

| Rule | Test |
| --- | --- |
| `DUPLICATE_TITLE` | normalised title equals another post's normalised title |
| `DUPLICATE_DESCRIPTION` | normalised description equals another post's |
| `SAME_TITLE_AND_LOCATION` | same normalised title **and** same normalised barangay |
| `MISSING_REQUIRED_INFORMATION` | description shorter than 40 characters, responsibilities shorter than 15 |
| `REPEATED_REJECTED_LISTING` | the post repeats a title/description that was already rejected |
| `SUSPICIOUS_PAYMENT_REQUEST` | the text contains any phrase from a fixed list ("processing fee", "training fee", …) |

The comparison is **normalised equality, not fuzzy matching**: there is no edit distance and no similarity threshold, and the documentation says so rather than claiming more. A flag never blocks publication by itself; it queues the post for an administrator, and a dismissed flag is not raised again for the same post.

**Cost.** `O(P × L)` — the employer's posts `P` (small) times the length `L` of the text being compared.

### 22.13 Skills-gap priority — a second, smaller weighted model

The skills-gap screen does not guess what a seeker should learn; it ranks missing skills with an explicit three-factor model, using the same renormalisation rule as the matching engine:

| Factor | Weight | Raw value | Normalisation |
| --- | --- | --- | --- |
| Local demand | 50 | number of active posts in the chosen area requiring the skill | ÷ the largest such count among candidates × 100 |
| Opportunity impact | 30 | jobs that would cross the seeker's match threshold if the skill were added | ÷ the largest such count × 100 |
| Preference relevance | 20 | share of posts requiring it that are in the seeker's municipality or employment type | already a percentage |

Priority score = `earned / applicable × 100`, then banded **HIGH ≥ 65**, **MEDIUM ≥ 35**, otherwise **LOW**. Ranking is by score, then opportunity count, then area demand, then skill name — a total order, so the list is stable between requests.

Two details make this defensible rather than decorative:

- The "opportunity impact" figure is produced by **re-running `MatchingEngine`** on a copy of the profile with the skill added. The promised improvement is the engine's own number, not a separate estimate that could disagree with the job list.
- A factor that has no data is dropped from both numerator and denominator (the `preferenceShare` is `null` when the seeker has neither a municipality nor an employment preference), exactly as in the main score.

**Cost.** For each missing skill, one match calculation per post requiring it, so `O(Σ jobs(skill) × (R + S))`. This is the most expensive read endpoint in the system and it is computed on demand, per request, with no cache.

### 22.14 Idempotent notifications — uniqueness as an algorithm

Notifications are generated by status changes, moderation decisions and reports. A retried request must not create a second notification, so every notification carries a `dedupe_key` built from the event (`app-42-submitted`, `job-17-approved`) and the insert relies on the unique index:

```
INSERT INTO notifications (user_id, dedupe_key, …)
VALUES (?, ?, …)
ON CONFLICT (user_id, dedupe_key) DO NOTHING
```

The guarantee is in the index, not in a `SELECT` followed by an `INSERT` — the latter would race under two concurrent requests. PostgreSQL treats `NULL` dedupe keys as distinct, so rows written before the column existed are unaffected.

**Cost.** One indexed insert; a duplicate costs one failed conflict check.

### 22.15 Pagination and ordering

Every list endpoint clamps the requested page and size (`clampPage`, `clampSize`) before building a `PageRequest`, so a client cannot ask for page 10,000 or 100,000 rows. Sorting is resolved from a whitelist (`resolveSort`) rather than from the request string, which is what prevents an "order by" injection and an expensive sort on an unindexed column.

**Cost.** `O(pageSize)` rows transferred; the offset cost is the database's, which is why the default list is ordered by an indexed column.

## 23. Complexity summary

| Algorithm | Inputs (real sizes) | Time | Space | Implemented in |
| --- | --- | --- | --- | --- |
| Match score | 5 criteria, ≤ tens of skills | `O(R + S)` expected | `O(S)` | `MatchingEngine` |
| Skill split + normalise | one text field | `O(L)` | `O(S)` | `splitSkills`, `normalizeSkill` |
| Location tier | 4 municipalities | `O(1)` | `O(1)` | `locationCriterion` + `NEARBY` |
| Experience parse | one short text | `O(L)` | `O(1)` | `parseRequirement` |
| Salary overlap | 4 numbers | `O(1)` | `O(1)` | `salaryCriterion` |
| Profile completeness | 6 checks | `O(1)` | `O(1)` | `profileCompleteness` |
| Point in polygon | 205 vertices (municipality), 13.1 (barangay) | `O(V)` | `O(1)` | `ringContains`, `contains` |
| Boundary tolerance | ring vertices | `O(V)` | `O(1)` | `distanceToRingMeters` |
| Haversine distance | 2 points | `O(1)` | `O(1)` | `GeoService.distanceKm` |
| Rate limit | one bucket row | `O(log N)` per request | `O(1)` | `RateLimiter.check` + upsert |
| Status transition | 7 states | `O(1)` | `O(1)` | `TRANSITIONS` lookup |
| Job search (paged) | 17 tables, indexed filters | index scan + `O(pageSize)` | `O(pageSize)` | `buildSpecification` + `findAll` |
| Job search (ranked) | capped at 1,000 rows | `O(k log k + k(R+S))` | `O(k)` | `rankJobs` |
| Suspicious rules | employer's posts `P` | `O(P × L)` | `O(1)` | `SuspiciousListingService` |
| Skills-gap priority | missing skills × posts per skill | `O(Σ Jₖ(R+S))` | `O(K)` | `SkillsGapService` |
| Notification dedupe | one row per event | `O(log N)` | `O(1)` | unique index + `ON CONFLICT` |

### 23.1 What the system deliberately does not use

| Not used | Why, in one line |
| --- | --- |
| Machine learning, embeddings, model files | the score must be explainable and reproducible from stored fields |
| Fuzzy string matching (Levenshtein, trigrams) | duplicates are caught by normalised equality; adding a distance threshold would flag innocent posts |
| R-tree / PostGIS spatial index | 125 barangays and a few hundred posts do not justify it; linear vertex scans are microseconds |
| Graph search beyond one hop | "nearby" is defined as a shared land border, not as the shortest path |
| Cache layer (Redis, in-process) | one instance, small tables; a cache would add a consistency problem without a measured need |
| Full-text search index | the keyword filter is a `LIKE` on top of indexed filters; a search index would add a dependency |
| Priority queue / heap | ranking sets are capped at 1,000 rows, so a full sort is simpler and fast enough |

Each row is a decision the panel can challenge, and each has a stated cost: the numbered lists and the 1,000-row cap are the places where scale would eventually force a different structure.

**PART III — CODE EXPLANATION**


## 24. The one-paragraph version

JSM is one Spring Boot application that serves both a JSON REST API and the HTML pages that call it. Requests pass a rate-limit filter, a JWT filter and Spring Security's role rules, then reach a controller. Controllers translate HTTP into method calls on services; services hold every rule (moderation, matching, transitions, analytics) and talk to the database only through Spring Data repositories. Responses are DTOs, never entities, so a password hash or an internal column cannot escape by accident. The front end is plain HTML and JavaScript with no build step; it holds a token in `localStorage` and calls the API on the same origin. Nothing about the system is learned at runtime — the match score is one deterministic formula in `MatchingEngine`, which is why the seeker's list, the employer's ranking and the admin statistics always agree.

---

## 25. Where the code lives

```
ENUVEX/
├── backend/src/main/java/com/jobskills/     Java application (146 files)
│   ├── Application.java                     entry point
│   ├── config/        7 files,   470 lines  security, JWT filter, user details, CORS, caching, OpenAPI
│   ├── controller/   18 files, 2,376 lines  one class per API area
│   ├── service/      15 files, 6,044 lines  all business rules
│   ├── repository/   15 files,   347 lines  Spring Data interfaces
│   ├── model/        29 files, 1,096 lines  15 entities + 13 enums
│   ├── dto/          55 files, 1,763 lines  request/response shapes
│   ├── ratelimit/     4 files,   323 lines  fixed-window limiter + PostgreSQL store
│   └── exception/     2 files,   140 lines  global error mapping
├── backend/src/main/resources/
│   ├── application.yml                      configuration (env-var driven)
│   ├── db/migration/V1…V9                   Flyway schema, never edited by hand
│   └── geo/barangays.geojson                125 barangays + boundaries
├── index.html, login.html, register.html, layout.html     public shell
├── job-seeker/  12 pages · employer/ 9 pages · admin/ 11 pages
├── assets/js/   api.js, role-app.js, jsm-map.js, main.js, jsm-match.js, …
├── assets/css/  the design system
├── backend/src/test/java/…                  59 tests
├── tools/       Python HTTP suites used in earlier phases
└── Dockerfile                               image the host builds
```

The package structure is the architecture: a class that speaks HTTP is in `controller`, a class that decides something is in `service`, and a class that reads a table is in `repository`. A reviewer can therefore tell what a file does from its folder.

---

## 26. Startup and configuration

`Application.java` is a two-line Spring Boot entry point (18 lines with the WAR initialiser). It starts the context, Flyway runs the migrations, Hibernate validates the schema against them, `GeoService` loads the barangay file, and only then does the server accept traffic.

`application.yml` is the only configuration file. **Nothing secret is written in it** — every value is either a safe default or an environment variable:

```yaml
spring:
  datasource:
    url: ${DB_URL:...}
  jpa:
    hibernate:
      ddl-auto: validate        # the migrations own the schema; the app may not alter it
  flyway:
    clean-disabled: true        # a stray "clean" can never drop the tables
jwt:
  secret: ${JWT_SECRET}         # no default: a fallback would be a public signing key
  expiration: ${JWT_EXPIRATION:3600000}
jsm:
  rate-limit:
    login-limit: ${JSM_RATE_LIMIT_LOGIN:10}
    registration-limit: ${JSM_RATE_LIMIT_REGISTRATION:5}
```

Two decisions are worth defending:

1. **`ddl-auto: validate`.** If a migration and an entity disagree, the application refuses to start instead of silently changing a production table. Schema drift is impossible, and the migration is the single source of truth.
2. **No default JWT secret.** The `@Value("${jwt.secret}")` in `JwtService` has no fallback on purpose. A built-in default in a public repository is a key anyone can use to sign their own admin token.

The four profiles of configuration (local, test, production) differ only in environment variables, never in code paths.

---

## 27. One request, step by step

Take `POST /api/job-seekers/apply` with a seeker's token. This is the literal order the code runs:

**Step 1 — `RateLimitFilter`** (in `ratelimit/`). It maps the request to one of four protected actions by method and exact path (sign-in, registration, report submission, job application) and asks `RateLimiter` for a decision. The counter is one PostgreSQL row per action and client address, incremented by a single upsert:

```sql
INSERT INTO rate_limit_counters AS c (bucket_key, window_start, hits, updated_at)
VALUES (?, ?, 1, CURRENT_TIMESTAMP)
ON CONFLICT (bucket_key) DO UPDATE SET
    hits = CASE WHEN c.window_start = EXCLUDED.window_start THEN c.hits + 1 ELSE 1 END,
    ...
RETURNING hits
```

The upsert is the whole point: two instances (or two simultaneous requests) cannot both read a stale count and both decide "allowed". The client key is the **last** entry of `X-Forwarded-For`, because that is the address the trusted proxy wrote — reading the first entry would let an attacker choose their own bucket and never be limited. If the counter table cannot be read, `RateLimiter` **allows** the request: a database hiccup must not lock people out.

**Step 2 — `JwtAuthenticationFilter`** (in `config/`). No `Authorization: Bearer …` header means "continue unauthenticated" and the security rules decide later. With a token, the filter extracts the e-mail, **reloads the account from the database**, and refuses to authenticate when the account is disabled:

```java
UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
boolean usableAccount = userDetails.isEnabled() && userDetails.isAccountNonLocked() && …;
if (usableAccount && jwtService.isTokenValid(jwt, userDetails)) { … }
```

That reload is why suspension takes effect immediately instead of waiting for the token to expire. The filter is deliberately **not** a `@Component`: registering it twice would run it twice per request.

**Step 3 — Spring Security rules** (`SecurityConfig`). Public: the HTML shell, `/assets/**`, `/api/auth/**`, `/api/health`, the registration lookups, `GET /api/geo/**` and `GET /api/jobs/**`. Everything else needs a valid token, and `anyRequest().authenticated()` is the default — new endpoints are protected unless someone deliberately opens them. Missing or invalid credentials answer **401, not 403**, so the front end can tell "sign in again" from "not allowed".

**Step 4 — the controller** turns the request into a call. `JobController` and friends read the caller's id from `CustomUserDetails` (or `SecurityUtils`), never from the request body:

```java
private Long extractUserId(Authentication authentication) {
    Object principal = authentication.getPrincipal();
    if (principal instanceof CustomUserDetails details) return details.getUserId();
    return null;
}
```

**Step 5 — the service** applies the rules and writes history/notifications in the same transaction.

**Step 6 — `StaticAssetCacheFilter`** runs last and lets CSS/JS be cached for ten minutes while HTML revalidates, because Spring Security stamps `no-store` on everything by default and that made every page re-download the whole design system.

**When something goes wrong**, `GlobalExceptionHandler` maps the exception to a status and a short message: `IllegalArgumentException`→400, `AccessDeniedException`→403, `DataIntegrityViolationException`→409, unreadable body→400, unknown page→404, unsupported method→405, anything else→500 with `"Internal server error"`. The real message is logged, never returned, because database errors can contain hosts and credentials.

---

## 28. The data model

Fifteen entities map to seventeen tables. Enums are stored as strings (`@Enumerated(EnumType.STRING)`), so a row stays readable in the database and reordering a Java enum can never change stored meaning.

| Entity | Table | Notes |
| --- | --- | --- |
| `User` | `users` | e-mail unique, BCrypt `passwordHash`, `userType`, `isActive` |
| `JobSeekerProfile` | `job_seeker_profiles` | skills, education, work experience, municipality + barangay, preferences |
| `EmployerProfile` | `employer_profiles` | business details, `verificationStatus` |
| `JobPost` | `job_posts` | requirements, salary range, coordinates, `moderationStatus` |
| `JobApplication` | `job_applications` | unique (seeker, post); stores the **match percentage at the time of applying** |
| `ApplicationStatusHistory` | `application_status_history` | append-only timeline with actor, role, reason |
| `ApplicationInterview` | `application_interviews` | one per application; schedule, mode, notes |
| `SavedJob`, `Notification`, `Report`, `SuspiciousListing`, `AuditLog`, `LearningResource`, `SkillLearningInterest`, `UploadedFile` | matching tables | see `DATABASE_DOCUMENTATION.md` |

Two design points a panel often asks about:

- **Why store `matchPercentage` on the application?** The score shown to the employer must be the score the seeker saw when they applied. Recomputing later would silently rewrite history when either profile changes — so the value is frozen at submission, and the live comparison is a separate calculation.
- **Why an append-only history table?** The current status alone cannot answer "when was I shortlisted, and by whom". History rows are only inserted, never updated, which is what makes the seeker timeline and the audit trail trustworthy.

---

## 29. Repositories: almost no SQL

Fifteen interfaces extend `JpaRepository`. Most methods are **derived queries** — the method name *is* the query, e.g. `findByUserIdAndIsReadOrderByCreatedAtDesc`, `countByUserTypeAndIsActiveTrue`. The few things Spring cannot derive are written as explicit `@Query`/`@Modifying` methods, such as the notification insert with `ON CONFLICT DO NOTHING` and the mark-all-read update.

Search is the one place with a dynamic query: `JobService.buildSpecification()` assembles a JPA `Specification` from whatever filters the request supplies (keyword, municipality, barangay, category, employment type, salary floor, distance, match band), always ANDed with `isActive = true` and `moderationStatus = APPROVED`. Predicates are built through the Criteria API with bound parameters, so a search box cannot inject SQL.

---

## 30. Services: one class per responsibility

| Service | Lines | Responsibility | Entry points |
| --- | --- | --- | --- |
| `MatchingEngine` | 518 | the score, its explanation and profile completeness | `calculateMatch`, `classification`, `profileCompleteness` |
| `ApplicationService` | 706 | application reads, transitions, withdrawal, interviews | `updateStatus`, `withdraw`, `scheduleInterview` |
| `JobService` | 721 | posting, moderation hand-off, search, map, applying | `createJob`, `searchJobs`, `applyForJob` |
| `SkillsGapService` | 768 | gap analysis, demand, simulation, career paths | `analyse`, `simulate`, `careers` |
| `EmployerAnalyticsService` | 1,021 | ten analytics views with date ranges | `overview`, `funnel`, `export` |
| `GeoService` | 562 | barangays, boundaries, pin validation, distance | `validate`, `boundaries`, `coverage` |
| `UserService` | 434 | registration, sign-in, profile updates | `register`, `login` |
| `SuspiciousListingService` | 301 | six detection rules | `evaluate` |
| `SkillsDemandService`, `SkillsAnalyticsService`, `AdminService`, `AdminMatchingService`, `ReportService`, `NotificationService`, `JwtService` | 36–232 each | demand stats, admin metrics, reports, notifications, tokens | — |

The rule that keeps this clean: **a controller never touches a repository, and a service never returns an entity to a controller**. `UserService.login` returns an `AuthResponse` (token, type, name, id) and the hash never leaves the service layer.

---

## 31. Deep dive: `MatchingEngine` — how a score is produced

The engine is a plain `@Service` with no state, no dependencies and no randomness. `calculateMatch(seeker, job)` builds five criteria, then does one sum:

```java
for (MatchCriterionDto criterion : criteria) {
    if (criterion.isApplicable()) {
        applicable += criterion.getWeight();
        earned     += criterion.getEarnedPoints();
    } else {
        complete = false;
    }
}
double overall = applicable > 0 ? (earned / applicable) * 100.0 : 0.0;
```

Each criterion method returns `(weight, applicable, earnedPoints, score, status, sentence)`. The sentence is not decoration: it is the explanation the seeker reads ("Same municipality (Daet), different barangay"), generated from the same numbers that produced the percentage, so the text and the score cannot disagree.

**Skills (40).** `splitSkills()` splits on commas, semicolons, pipes and bullets, then `normalizeSkill()` lower-cases, collapses spaces, fixes curly apostrophes and trims punctuation — so `"Customer Service"`, `"customer service"` and `" customer service. "` are the same skill. Score = matched ÷ required × 100. An employer who listed no skills makes the criterion **unavailable** rather than zero.

**Location (25).** Same barangay 100, same municipality 80, bordering municipality 50 (`NEARBY` map: Basud–Daet, Daet–Vinzons, Daet–Labo, Vinzons–Labo, Labo–Basud), otherwise 10.

**Employment type (15).** Exact preference 100; a related type 60 through `COMPATIBLE_TYPES` (full-time ↔ contract, part-time ↔ contract/temporary, contract → full/part/temporary, temporary → contract/part-time); anything else 0.

**Experience (10).** `parseRequirement()` reads free text — "no experience", "entry level", "fresh graduate" mean zero; otherwise the first number is the minimum, a second number is the maximum, and "2-3 years" is normalised so the smaller value is the minimum. `seekerYears()` takes an explicit "N years" from the profile and otherwise counts a non-empty work history as one year, because the seeker has recorded at least one job. Meeting the minimum is 100, partial is a proportional score.

**Salary (10).** Both ranges are optional on both sides. Meeting the expected minimum is 100; an overlap is `50 + 50 × (overlap ÷ span)`, so a range that only touches the expectation still earns something; no overlap is 0. A missing range on either side makes the criterion unavailable.

**What "unavailable" means.** The weight leaves the denominator *and* the numerator. That is the difference between an honest score and a flattering one: a job with no listed skills does not give a seeker 40 free points, and a seeker with no expected salary is not punished for it — the response carries `complete = false` and the UI says the score rests on the criteria that could be judged.

**Determinism.** Two identical inputs give the identical result on any instance, which the Phase 9 audit confirmed by scoring the same case on 50 freshly constructed engines. The same class is used by job search, the employer's applicant list and the admin statistics — so there is exactly one definition of "match" in the system.

---

## 32. Deep dive: `ApplicationService` — a state machine, not a status field

The allowed moves are declared once, as data:

```java
TRANSITIONS.put(ApplicationStatus.SUBMITTED,
    EnumSet.of(REVIEWING, SHORTLISTED, REJECTED));
TRANSITIONS.put(ApplicationStatus.WITHDRAWN, EnumSet.noneOf(ApplicationStatus.class));
```

`updateStatus()` then refuses anything else, in this order: find the application → prove the caller owns the job (`requireJobOwner`) → refuse a no-op → refuse a move outside the map → refuse `WITHDRAWN` from an employer ("Only the job seeker can withdraw an application"). Only then does it save, record history and notify.

`withdraw()` is the seeker's mirror image: ownership is checked against the profile, a final application can no longer be withdrawn (`current.isOpen()`), and the employer is notified with a dedupe key.

Interviews are guarded differently: an application may have **one** interview, so a second attempt answers "Reschedule it instead" instead of silently creating a duplicate.

Every write goes through the same `record()` helper, which appends `(previous, new, event, actorId, actorRole, actorName, reason)`. The timeline endpoint and the audit trail therefore read one table rather than reconstructing events from the current row.

---

## 33. Deep dive: `JobService` — from draft to a filled position

`createJob()` validates and normalises the request (salary maximum ≥ minimum, required text fields), resolves the location through `GeoService`, and stores the post with `moderationStatus = PENDING_REVIEW`. A new post is **invisible to the public** until an administrator approves it.

`applyForJob()` is short and refuses four things before writing anything:

```java
if (!job.isActive()) throw new IllegalArgumentException("Job is not active");
if (job.getModerationStatus() != ModerationStatus.APPROVED) …;
if (applicationRepository.existsByJobSeekerIdAndJobPostId(seeker.getId(), jobId)) …;
```

It then stores the application **with the match percentage calculated at that moment**, writes the `SUBMITTED` history row, and notifies both sides with keys like `app-<id>-submitted`, so a retried request cannot produce duplicate notifications.

`searchJobs()` builds the specification, applies paging and sorting, and — only when the caller is a signed-in seeker — attaches the match percentage to each result. The public listing and the seeker listing therefore share one query; the only difference is whether a seeker is passed in.

`searchJobsMap()` reuses the same specification but projects to map markers and drops posts without a confirmed pin, so the map can never show a job the list would not.

---

## 34. Deep dive: sign-in and tokens

`UserService.register()` creates the `User` plus the matching profile in one transaction, hashes the password with BCrypt, and returns a token immediately. An employer starts `PENDING`; an administrator can never be created through the public endpoint.

`UserService.login()` has two paths. The normal path looks the account up by normalised e-mail, checks `passwordEncoder.matches(...)`, then checks `isActive()`. The second path exists for the defense demo: typing `admin` with the configured access code signs the administrator in — but only while `JSM_ADMIN_ACCESS_CODE` is set, compared in constant time, and refused entirely when the variable is empty. A public deployment therefore cannot be taken over by typing a known name.

`JwtService` issues a 1-hour access token and a 7-day refresh token, both HS256, carrying the e-mail as subject plus `userId` and `userType` claims. The signature key comes from `JWT_SECRET` with no fallback.

---

## 35. Moderation and administration

**`SuspiciousListingService`** evaluates six rules when a post is submitted or edited, and each flag records which rule fired:

| Rule | What it looks for |
| --- | --- |
| `DUPLICATE_TITLE` | same normalised title already posted |
| `DUPLICATE_DESCRIPTION` | near-identical description |
| `SAME_TITLE_AND_LOCATION` | same title in the same place |
| `MISSING_REQUIRED_INFORMATION` | description shorter than 40 characters, responsibilities shorter than 15 |
| `REPEATED_REJECTED_LISTING` | an employer re-posting something already rejected |
| `SUSPICIOUS_PAYMENT_REQUEST` | phrases asking applicants to pay (processing fee, training fee, …) |

Flags never block a post by themselves — they queue it for the administrator, who can dismiss one with a written reason, and a dismissed flag is not raised again for the same post.

**`AdminController`** (863 lines) is the largest controller because it is one screen per administrative job: verification, moderation, reports, suspicious listings, users, audit logs. Its helpers carry the important guarantees:

```java
if (target.getId().equals(admin.getId())) throw …("You cannot suspend your own administrator account.");
if (target.getUserType() == UserType.ADMIN
    && userRepository.countByUserTypeAndIsActiveTrue(UserType.ADMIN) <= 1)
    throw …("Cannot suspend the last active administrator.");
```

Every decision also calls `audit(action, entityType, entityId, from, to, reason)`, which inserts an `audit_logs` row. Nothing in the API can update or delete an audit row — the test suite asserts that the routes do not exist.

---

## 36. Geography: a data problem, solved once

`GeoService` loads `barangays.geojson` at start-up and builds indexes by PSGC code and by municipality, so a lookup is a map access rather than a scan. It validates every employer-supplied pin:

- the municipality must be one of the four coverage codes (`0501601000` Basud, `0501603000` Daet, `0501606000` Labo, `0501612000` Vinzons);
- the barangay must belong to that municipality, and the service returns the official code and the canonical name — the client's spelling is never trusted;
- the point must be inside the municipality polygon, with a **250 m tolerance** because the boundaries are simplified to 1 % of their vertices.

Distance search uses `distanceKm()`, a haversine calculation on stored coordinates. Map tiles are configuration (`jsm.map.*`), served to the browser through `/api/geo/map-config`, so no page hard-codes a provider.

---

## 37. Skills gap and analytics: counting, not guessing

`SkillsGapService.analyse()` compares the seeker's skills with the skills actually demanded by approved posts in a chosen area and category, producing three lists: skills the seeker has and the market wants, skills the market wants and the seeker lacks, and the seeker's skills nobody is asking for. `simulate()` answers "what if I learned X?" by adding X to a copy of the profile and calling the **same** `MatchingEngine` — so a promised improvement is the engine's own number, not a separate estimate. `careers()` ranks categories by the number of matching posts.

`EmployerAnalyticsService` builds ten views (overview, per-post performance, funnel, response times, matching insights, skills analysis, geography, insights, export, and the dashboard counters) from the employer's own jobs and applications, filtered by date range and always scoped by `employerId`. There is no cross-employer aggregation anywhere in the class.

---

## 38. The front end: no framework, no build step

| File | Role |
| --- | --- |
| `assets/js/api.js` | the only place that talks HTTP: base URL, token, timeout, refresh, error mapping |
| `assets/js/role-app.js` | shared shell for employer and admin consoles (sidebar, header, notifications) |
| `assets/js/jsm-match.js` | renders the match breakdown consistently everywhere |
| `assets/js/jsm-map.js` | loads Leaflet 1.9.4 + marker clustering on demand and draws pins/boundaries |
| `main.js`, `landing.js`, `jsm-inbox.js` | landing page, public pages, notification inbox |
| `job-seeker/`, `employer/`, `admin/` | 32 role pages plus 4 public pages |

Two details that show the design intent:

```js
const API_BASE_URL = window.JSM_API_BASE || (JSM_PACKAGED_SHELL ? JSM_REMOTE_API : '/api');
const API_TIMEOUT_MS = 120000;   // the free instance may be booting, that is slow, not an error
```

The API is same-origin, so the same build works on localhost, on Render and on a custom domain without touching CORS. The two-minute timeout exists because the free hosting tier sleeps: a cold request legitimately takes a minute, and a browser default would report it as a failure.

Every page calls `ApiClient` and renders from JSON. There is no server-side templating, which is why the HTML pages can be inspected in a browser's view-source and why the deliverable has no npm dependencies to install.

---

## 39. Tests: what the 59 prove

| Class | Tests | What it pins down |
| --- | --- | --- |
| `MatchingEngineTest` | 25 | every criterion, weights, unavailable criteria, band boundaries, skill parsing, completeness |
| `SuspiciousListingServiceTest` | 12 | each rule fires once, dismissal sticks, no false positive on a clean post |
| `RateLimiterTest` | 8 | limit, per-action and per-address isolation, window reset, disable switch, fail-open |
| `ReportServiceTest` | 7 | report lifecycle and statistics |
| `ModerationAuthorizationTest` | 6 | admin-only routes, audit logs read-only, employer cannot verify itself |
| `JobSkillsMatchingSystemApplicationTests` | 1 | the context boots against the real schema |

They are integration-style tests against PostgreSQL (not mocks of the database), so a broken migration or a wrong column type fails the suite. Run them with:

```bash
cd backend
DB_URL="jdbc:postgresql://localhost:5432/jsm" DB_USERNAME=postgres DB_PASSWORD=… \
JWT_SECRET="local-test-secret-key-0123456789abcdef" mvn test
```

---

## 40. Conventions and decisions worth defending

1. **Layering is enforced by habit, not by a framework.** Controller → service → repository, no shortcuts; the compiler does not stop a controller from injecting a repository, the code review does.
2. **DTOs at the boundary.** 55 DTOs exist so that an entity change never silently changes an API response, and a lazy JPA relation never leaks into JSON.
3. **Errors are statuses, not stack traces.** `GlobalExceptionHandler` centralises the mapping; the 500 branch returns a fixed sentence and logs the detail.
4. **Configuration over constants.** Rate limits, JWT lifetimes, map tiles and zoom levels are properties, so the same build runs locally and in production.
5. **Every rule has one home.** The score lives only in `MatchingEngine`; transitions only in `ApplicationService`; suspicious rules only in `SuspiciousListingService`. That is why the numbers on three different dashboards agree.
6. **Comments explain why, not what.** The comments in this codebase record decisions ("no default JWT secret", "last entry of X-Forwarded-For", "not a @Component on purpose") — each one marks a bug that was fixed once and could return.
7. **No silent failures.** A rate limiter that cannot count allows the request and logs a warning; a token that cannot be parsed leaves the request unauthenticated so the rules answer 401. Failing closed everywhere would take the site down for a database blip; failing open only where the consequence is acceptable is the deliberate choice.

---

## 41. Reading order for the panel

If someone wants to check the code in fifteen minutes:

1. `Application.java` → `application.yml` (how it starts, what it needs).
2. `config/SecurityConfig.java` (which routes are public and why).
3. `config/JwtAuthenticationFilter.java` (how a caller becomes a user).
4. `service/MatchingEngine.java` (the claim the whole project rests on).
5. `service/ApplicationService.java` lines 33–60 and `updateStatus` (the state machine).
6. `service/JobService.java` `applyForJob` (the write path, and the four refusals).
7. `controller/JobController.java` (how thin a controller is here).
8. `ratelimit/` (the only concurrency-sensitive code).
9. `assets/js/api.js` (how the front end authenticates).
10. `backend/src/test/java/…` (what is actually asserted).

---

*Companion sections: Part I of this document (the system as a whole), `docs/final-release/API_DOCUMENTATION.md` (all 120 routes), `docs/final-release/DATABASE_DOCUMENTATION.md` (every table and column), `docs/final-release/MATCHING_ENGINE.md` (the scoring rules in full).*

---

*End of document. The PDF is generated from this Markdown file by `python tools/md_to_pdf.py docs/JSM_COMPLETE_DOCUMENTATION.md`, so the printable copy and the source text cannot drift apart.*
