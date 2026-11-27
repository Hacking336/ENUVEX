# JSM — Code Explanation for Defense

**Scope:** how the source code is organised and how its main parts work, written so a panel can follow it without reading 12,000 lines.
**Repository:** `Hacking336/ENUVEX`, branch `main`, commit `b207ee0` (the commit audited in Phase 9).
**Counts below were produced from the tree, not estimated:** 146 Java files, 12,577 lines of application code, 1,146 lines of test code, 18 controllers, 120 mapped endpoints, 15 entities and 13 enums, 17 tables, 9 migrations, 36 HTML pages, 9 JavaScript modules.

---

## 1. The one-paragraph version

JSM is one Spring Boot application that serves both a JSON REST API and the HTML pages that call it. Requests pass a rate-limit filter, a JWT filter and Spring Security's role rules, then reach a controller. Controllers translate HTTP into method calls on services; services hold every rule (moderation, matching, transitions, analytics) and talk to the database only through Spring Data repositories. Responses are DTOs, never entities, so a password hash or an internal column cannot escape by accident. The front end is plain HTML and JavaScript with no build step; it holds a token in `localStorage` and calls the API on the same origin. Nothing about the system is learned at runtime — the match score is one deterministic formula in `MatchingEngine`, which is why the seeker's list, the employer's ranking and the admin statistics always agree.

---

## 2. Where the code lives

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

## 3. Startup and configuration

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

## 4. One request, step by step

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

## 5. The data model

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

## 6. Repositories: almost no SQL

Fifteen interfaces extend `JpaRepository`. Most methods are **derived queries** — the method name *is* the query, e.g. `findByUserIdAndIsReadOrderByCreatedAtDesc`, `countByUserTypeAndIsActiveTrue`. The few things Spring cannot derive are written as explicit `@Query`/`@Modifying` methods, such as the notification insert with `ON CONFLICT DO NOTHING` and the mark-all-read update.

Search is the one place with a dynamic query: `JobService.buildSpecification()` assembles a JPA `Specification` from whatever filters the request supplies (keyword, municipality, barangay, category, employment type, salary floor, distance, match band), always ANDed with `isActive = true` and `moderationStatus = APPROVED`. Predicates are built through the Criteria API with bound parameters, so a search box cannot inject SQL.

---

## 7. Services: one class per responsibility

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

## 8. Deep dive: `MatchingEngine` — how a score is produced

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

## 9. Deep dive: `ApplicationService` — a state machine, not a status field

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

## 10. Deep dive: `JobService` — from draft to a filled position

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

## 11. Deep dive: sign-in and tokens

`UserService.register()` creates the `User` plus the matching profile in one transaction, hashes the password with BCrypt, and returns a token immediately. An employer starts `PENDING`; an administrator can never be created through the public endpoint.

`UserService.login()` has two paths. The normal path looks the account up by normalised e-mail, checks `passwordEncoder.matches(...)`, then checks `isActive()`. The second path exists for the defense demo: typing `admin` with the configured access code signs the administrator in — but only while `JSM_ADMIN_ACCESS_CODE` is set, compared in constant time, and refused entirely when the variable is empty. A public deployment therefore cannot be taken over by typing a known name.

`JwtService` issues a 1-hour access token and a 7-day refresh token, both HS256, carrying the e-mail as subject plus `userId` and `userType` claims. The signature key comes from `JWT_SECRET` with no fallback.

---

## 12. Moderation and administration

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

## 13. Geography: a data problem, solved once

`GeoService` loads `barangays.geojson` at start-up and builds indexes by PSGC code and by municipality, so a lookup is a map access rather than a scan. It validates every employer-supplied pin:

- the municipality must be one of the four coverage codes (`0501601000` Basud, `0501603000` Daet, `0501606000` Labo, `0501612000` Vinzons);
- the barangay must belong to that municipality, and the service returns the official code and the canonical name — the client's spelling is never trusted;
- the point must be inside the municipality polygon, with a **250 m tolerance** because the boundaries are simplified to 1 % of their vertices.

Distance search uses `distanceKm()`, a haversine calculation on stored coordinates. Map tiles are configuration (`jsm.map.*`), served to the browser through `/api/geo/map-config`, so no page hard-codes a provider.

---

## 14. Skills gap and analytics: counting, not guessing

`SkillsGapService.analyse()` compares the seeker's skills with the skills actually demanded by approved posts in a chosen area and category, producing three lists: skills the seeker has and the market wants, skills the market wants and the seeker lacks, and the seeker's skills nobody is asking for. `simulate()` answers "what if I learned X?" by adding X to a copy of the profile and calling the **same** `MatchingEngine` — so a promised improvement is the engine's own number, not a separate estimate. `careers()` ranks categories by the number of matching posts.

`EmployerAnalyticsService` builds ten views (overview, per-post performance, funnel, response times, matching insights, skills analysis, geography, insights, export, and the dashboard counters) from the employer's own jobs and applications, filtered by date range and always scoped by `employerId`. There is no cross-employer aggregation anywhere in the class.

---

## 15. The front end: no framework, no build step

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

## 16. Tests: what the 59 prove

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

## 17. Conventions and decisions worth defending

1. **Layering is enforced by habit, not by a framework.** Controller → service → repository, no shortcuts; the compiler does not stop a controller from injecting a repository, the code review does.
2. **DTOs at the boundary.** 55 DTOs exist so that an entity change never silently changes an API response, and a lazy JPA relation never leaks into JSON.
3. **Errors are statuses, not stack traces.** `GlobalExceptionHandler` centralises the mapping; the 500 branch returns a fixed sentence and logs the detail.
4. **Configuration over constants.** Rate limits, JWT lifetimes, map tiles and zoom levels are properties, so the same build runs locally and in production.
5. **Every rule has one home.** The score lives only in `MatchingEngine`; transitions only in `ApplicationService`; suspicious rules only in `SuspiciousListingService`. That is why the numbers on three different dashboards agree.
6. **Comments explain why, not what.** The comments in this codebase record decisions ("no default JWT secret", "last entry of X-Forwarded-For", "not a @Component on purpose") — each one marks a bug that was fixed once and could return.
7. **No silent failures.** A rate limiter that cannot count allows the request and logs a warning; a token that cannot be parsed leaves the request unauthenticated so the rules answer 401. Failing closed everywhere would take the site down for a database blip; failing open only where the consequence is acceptable is the deliberate choice.

---

## 18. Reading order for the panel

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

*Companion documents: `DEFENSE_DOCUMENTATION.md` (the system as a whole), `docs/final-release/API_DOCUMENTATION.md` (all 120 routes), `docs/final-release/DATABASE_DOCUMENTATION.md` (every table and column), `docs/final-release/MATCHING_ENGINE.md` (the scoring rules in full).*
