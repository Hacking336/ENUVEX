# Testing and QA Report

**JSM — final release.** This report records what was actually executed. Counts from overlapping suites are **not** summed into a single "unique tests" number.

---

## 1. How to read the numbers

| Category | Meaning |
| --- | --- |
| **Automated unit tests** | JUnit tests run by `mvn test`. Each test method is counted once. |
| **Integration test** | The Spring `contextLoads` test: starts the full context, runs Flyway against a real PostgreSQL, validates every entity mapping. |
| **Scripted API checks** | Python suites in `tools/` that call a running instance over HTTP. They overlap each other (the Phase 6 suites re-check some Phase 7 items), so their counts are reported per suite, never added together. |
| **Browser checks** | Rendered pages inspected in a real browser at several widths. |
| **Production smoke checks** | Unauthenticated requests against the deployed service. |

## 2. Automated unit and integration tests (this release)

`cd backend && mvn test` with a real PostgreSQL:

```
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

| Test class | Tests | What it proves |
| --- | --- | --- |
| `MatchingEngineTest` | 25 | Weights, each criterion's scoring, missing-data handling, band mapping, explanation text, skill parsing/normalisation |
| `SuspiciousListingServiceTest` | 12 | Each detection rule triggers once, dismissal stops re-flagging, reasons are human-readable |
| `ReportServiceTest` | 7 | Report lifecycle transitions and statistics |
| `ModerationAuthorizationTest` | 6 | Admin routes are `hasRole('ADMIN')`, audit logs read-only, report submission takes no reporter id, employers cannot self-verify |
| `RateLimiterTest` | 7 | Allow up to the limit, refuse after, per-address and per-action isolation, window reset, disable switch, fail-open on database error, route coverage |
| `JobSkillsMatchingSystemApplicationTests` | 1 | Full context boot, Flyway to V9, `ddl-auto=validate` passes |

Phase-by-phase history of these tests: matching (Phase 2) 25 tests, moderation service and authorization (Phase 6) 25 tests, rate limiting (Phase 8) 7 tests, context/migration 1. The 58 is the current total of distinct test methods.

**What the unit tests do not cover:** HTTP status codes end-to-end (that is the scripted suites), concurrency, and any browser behaviour.

## 3. Database and migration tests

| Test | Result |
| --- | --- |
| Fresh database, V1→V8 applied, then `ddl-auto=validate` (Phase 7) | **PASS** — 8 migrations in 241 ms, health `UP`, empty listings, schema validated; database dropped afterwards |
| V9 applied to the development database (Phase 8) | **PASS** — `Successfully applied 1 migration … now at version v9`, rate limiting active |
| Upgrade of a populated database (V8 backfill: existing posts → `APPROVED`, verified employers → `VERIFIED`) | **PASS** — re-checked live in Phase 7; no row deleted or unpublished |
| Restricted destructive operations | **PASS** — `flyway.clean-disabled: true` |

## 4. Scripted API checks

| Suite | Checks | Scope | Result |
| --- | --- | --- | --- |
| `tools/phase7_security_check.py` | 50 | Admin access-code gate, role separation (seeker/employer/admin), tampered token, suspension kills an existing session, restoration, page-size cap, 400-not-500 cases, cross-employer IDOR on applications/skills/applicants, analytics endpoints, security headers, health, unknown route | **50 / 50 pass** (local instance, Phase 7) |
| `tools/phase6_moderation_check.py` | 92 | Employer verification, job approval/rejection/changes/restore, report lifecycle, suspicious detection and dismissal, suspension/restoration, audit writes | **92 / 92 pass** |
| `tools/phase6_job_moderation_check.py` | 36 | Employer-facing moderation states and resubmission flow | **36 / 36 pass** |
| Phase 8 rate-limit check (manual, this release) | 12 sign-in attempts + 3 unrelated endpoints | Limit at 20/min (config used 10/min on the instance under test) | **PASS** — first 10 answered `400`, then `429` with `Retry-After`, `X-RateLimit-*` headers; `GET /api/jobs` 200, registration 400, report 401 unaffected |

These suites overlap: the same authentication behaviour is asserted by the Phase 7 suite and by parts of the Phase 6 suites. Treat 178 as "HTTP assertions executed", not as unique tests.

## 5. Browser, responsive and accessibility checks

| Check | Result |
| --- | --- |
| Eight role pages rendered with a real session at 1440 px (job search, job map, job detail with match, skills & career, applications, employer dashboard, applicants, analytics) | **PASS** — real data from the API, no error banners, no JavaScript exceptions |
| Analytics console at 1440 / 1024 / 768 / 390 / 360 px (Phase 7) | **PASS** — 0 px horizontal overflow at every width; the 21-row table scrolls inside `.table-wrap` |
| Other pages after Phase 7/8 changes | **PASS** — markup and CSS unchanged since the Phase 6 verification; headers are server-side only |
| Accessibility DOM checks on 4 pages (Phase 7) | 1 finding: the analytics job `<select>` had no accessible name → fixed with `aria-label` |
| Link integrity (Phase 8, static scan of 36 pages) | **PASS** — every local `href`/`src` resolves; the only apparent hits were JavaScript template strings and a `../../login.html` that the browser resolves to `/login.html` |
| Page titles | **PASS** — every page has a distinct, correct `<title>` (checked on all 33 pages) |
| Keyboard-only navigation and focus trapping | **NOT TESTED** |
| Colour contrast measurement | **NOT TESTED** (no contrast tooling available; brand palette unchanged) |
| Mobile in-app browser differences | **NOT TESTED** |

## 6. Production smoke checks

### Phase 7 deployment (`dep-dc42riajnfac73bbm1c0`, commit `57312ea` → `81e31f0`)

| Check | Result |
| --- | --- |
| `GET /api/health` | **PASS** — `200 {"status":"UP","database":"UP"}` |
| Hardening headers (`X-Frame-Options`, `X-Content-Type-Options`, `Referrer-Policy`, `Permissions-Policy`) | **PASS** |
| `/api/admin/moderation-overview` anonymous | **PASS** — `401` |
| `admin++` with an empty password | **PASS** — `400 Invalid admin credentials` |
| Malformed JSON to `/api/auth/login` | **PASS** — `400`, not `500` |
| `GET /api/jobs?size=1000` | **PASS** — `size` reported as 100 |
| `GET /api/jobs/1` | **PASS** — `200`, `moderationStatus: APPROVED` |
| `GET /employer/analytics.html` | **PASS** — `200`, contains the rewritten script |

### Phase 8 deployment

Recorded in `RELEASE_CHECKLIST.md` after the deploy, including the rate-limiting behaviour on the live service.

## 7. Blocked or unverified

| Area | Status | Why |
| --- | --- | --- |
| Administrator sign-in **on production** with the real access code | **BLOCKED** | The code is a deployment secret; using it in a command would mean reproducing it. The mechanism is covered locally and the identical code path is deployed |
| Production moderation writes (verify employer, approve/reject post, resolve report, suspend account) | **NOT TESTED on production** | They change production data and accounts, which requires explicit authorisation. All were exercised locally |
| Authenticated production page sweep | **NOT TESTED** | Requires production credentials in commands |
| Load, concurrency and stress testing | **NOT TESTED** | No load-testing environment; single free instance |
| Email/SMS delivery | **NOT APPLICABLE** | Not implemented |
| Screen-reader walkthrough | **NOT TESTED** | No screen reader in the environment |

## 8. Phase 1–7 test records referenced here

| Phase | Evidence | Where |
| --- | --- | --- |
| 2 — Matching | 25 unit tests on weights, criteria, bands, explanations | `MatchingEngineTest` |
| 3 — Tracking | Scripted flows for status changes, timeline, interviews, notifications | Phase 6 suites + manual browser checks |
| 5 — Analytics | Nine endpoints exercised; page rendered with real data; CSV built and inspected | Phase 7 analytics repair, browser checks |
| 6 — Moderation | 128 scripted checks across two suites + 25 unit tests | `tools/phase6_*.py` |
| 7 — Security | 50-check suite + headers/IDOR/session tests + this report | `tools/phase7_security_check.py` |

## 9. What is still unproven

- No independent security audit and no penetration test.
- No user-acceptance testing with real employers or job seekers, therefore no claim about satisfaction, adoption or hiring outcomes. The project has **no** survey results, no usability scores and no benchmark comparisons, and none are claimed anywhere in this documentation.
- Performance numbers measured locally are single-instance, small-dataset observations and are reported as such in `PROJECT_OVERVIEW.md`/`SECURITY_AND_LIMITATIONS.md`; they are not a benchmark.
