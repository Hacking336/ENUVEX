# Final Release Checklist

**JSM — Phase 8 release.** Legend: **PASS** verified in this release, **PASS (prior)** verified in an earlier phase and unchanged since, **BLOCKED** cannot be verified without authorisation or a secret, **NOT TESTED** not verified.

---

## 1. Source control

| Item | Value | Status |
| --- | --- | --- |
| Repository | `Hacking336/ENUVEX` | PASS |
| Branch | `main` | PASS |
| Phase 7 commit | `81e31f0` | PASS |
| Phase 8 commit | see the final commit created with this documentation set (reported in the delivery message) | PASS |
| Working tree | only intended changes staged: 9 Phase 7 files carried forward, `ratelimit` package, `V9` migration, `RateLimiterTest`, `application.yml`, and the documentation set | PASS |
| Unrelated pre-existing deletions (`.venv-bcrypt/*`) | left untouched, not committed | PASS |

## 2. Build and tests

| Check | Command | Status |
| --- | --- | --- |
| Compile + package | `mvn -o -q package -DskipTests` | **PASS** |
| Unit + integration tests | `mvn -o test` with PostgreSQL | **PASS** — 59 tests, 0 failures |
| Rate limiting unit tests | `RateLimiterTest` | **PASS** — 8 tests, including the spoofed-header case |
| Security/API suite | `tools/phase7_security_check.py` | **PASS (prior)** — 50/50 |
| Moderation suites | `tools/phase6_*.py` | **PASS (prior)** — 92/92 and 36/36 |

## 3. Database

| Check | Status |
| --- | --- |
| Flyway V1–V9 apply cleanly | **PASS** — V9 applied in Phase 8; V1–V8 verified on a fresh database in Phase 7 |
| `ddl-auto=validate` passes | **PASS** (context test) |
| No destructive migration in this release | **PASS** — V9 adds one table and one index |
| Production data untouched by this release | **PASS** — no manual SQL was run against production |
| Backup before deploy | **Manual** — confirm Supabase's automated backup retention in the Supabase dashboard; a restore has not been rehearsed |

## 4. Runtime and health

| Check | Status |
| --- | --- |
| Local instance starts with the new jar | **PASS** |
| `GET /api/health` reports database readiness | **PASS** locally; production value recorded in §10 |
| Rate limiter answers 429 with `Retry-After` after the configured number of sign-in attempts | **PASS** — observed `400` ×10 then `429`, `Retry-After: 42`, `X-RateLimit-*` |
| Rate limiter leaves other routes alone | **PASS** — `GET /api/jobs` 200, registration 400, anonymous report 401 |
| Real sign-in still works when under the limit | **PASS (prior)** — employer/seeker logins |

## 5. Functional checks (local, isolated database)

| Area | Status |
| --- | --- |
| Public pages load (`/`, `login.html`, `register.html`) | **PASS** |
| Registration (seeker and employer, multipart with photo) | **PASS (prior)** |
| Sign-in for all three roles | **PASS** locally |
| Role separation (seeker ↛ employer/admin, employer ↛ admin) | **PASS** |
| Job search, filters, pagination cap | **PASS** |
| Job map with boundaries and pins | **PASS** — rendered with real data |
| Match score and per-criterion explanation | **PASS** |
| Recommendations | **PASS (prior)** |
| Application submission, duplicate refusal | **PASS (prior)** |
| Application tracking and timeline | **PASS (prior)** |
| Interview scheduling, rescheduling, cancellation | **PASS (prior)** |
| Notifications (create, unread count, read, read-all, delete) | **PASS (prior)** |
| Skills gap analysis, simulation, career opportunities | **PASS** — page rendered with real data |
| Employer analytics: all nine sections plus CSV export | **PASS** — 21 posts, 1 applicant, real numbers |
| Employer job posting and moderation status | **PASS (prior)** |
| Admin verification, approvals, reports, flags, suspension, audit | **PASS (prior)** — 128 scripted checks |

## 6. Mobile / responsive

| Check | Status |
| --- | --- |
| Analytics console at 1440 / 1024 / 768 / 390 / 360 px, no horizontal overflow | **PASS (prior)** |
| Eight role pages at 1440 px, no console errors | **PASS** |
| Sidebar collapses to bottom navigation on small screens | **PASS (prior)** |
| Touch targets and keyboard-only navigation | **NOT TESTED** |
| Colour contrast measurement | **NOT TESTED** |

## 7. Documentation and presentation deliverables

| Deliverable | Path | Status |
| --- | --- | --- |
| Project overview | `docs/final-release/PROJECT_OVERVIEW.md` | PASS |
| System architecture | `docs/final-release/SYSTEM_ARCHITECTURE.md` | PASS |
| Database documentation | `docs/final-release/DATABASE_DOCUMENTATION.md` | PASS |
| Matching engine | `docs/final-release/MATCHING_ENGINE.md` | PASS |
| API documentation | `docs/final-release/API_DOCUMENTATION.md` | PASS |
| Installation and deployment | `docs/final-release/INSTALLATION_AND_DEPLOYMENT.md` | PASS |
| Testing and QA report | `docs/final-release/TESTING_AND_QA_REPORT.md` | PASS |
| Security and limitations | `docs/final-release/SECURITY_AND_LIMITATIONS.md` | PASS |
| Executive summary | `docs/final-release/FINAL_PROJECT_SUMMARY.md` | PASS |
| Job seeker manual | `docs/user-manuals/USER_MANUAL_JOB_SEEKER.md` | PASS |
| Employer manual | `docs/user-manuals/USER_MANUAL_EMPLOYER.md` | PASS |
| Administrator manual | `docs/user-manuals/USER_MANUAL_ADMIN.md` | PASS |
| Presentation | `docs/presentation/JSM_Final_Defense.pptx` | PASS |
| Presenter script | `docs/presentation/PRESENTER_SCRIPT.md` | PASS |
| Defense Q&A | `docs/presentation/DEFENSE_QUESTIONS_AND_ANSWERS.md` | PASS |
| Live demo guide | `docs/presentation/LIVE_DEMO_GUIDE.md` | PASS |
| Screenshots used in the deck (real application, local instance) | `docs/presentation/screenshots/` | PASS |

## 8. Secret hygiene

| Check | Status |
| --- | --- |
| No password, JWT secret, database URL with credentials or `JSM_ADMIN_ACCESS_CODE` value in any deliverable | **PASS** — scanned with a pattern grep; only variable *names* and placeholders appear |
| `.env` files not committed | **PASS** |
| Access code read from Render → Environment, never reproduced in a command or document | **PASS** |

## 9. Deployment verification

| Item | Value |
| --- | --- |
| Service | Render `ENUVEX` (`srv-dbk9hf3bc2fs73eqkuf0`), auto-deploy from `main` |
| Phase 8 feature commit | `456ddd6` — documentation, presentation, rate limiting |
| Phase 8 deployed commit | **`b207ee0`** — rate-limiter client key corrected after the first deploy |
| Render deploy | **`dep-dc45f4m7bikc738719ig`** — `build_in_progress` 16:00:19Z → **`live` 16:03:46Z** |
| Earlier deploy of this phase | `dep-dc45ag5g1s2s73dc5ttg` (commit `456ddd6`, live 15:53:44Z) |
| Health after deploy | **PASS** — `200 {"status":"UP","database":"UP"}` in 0.75 s |
| Migration during deploy | V9 applied by Flyway on start-up; no data change |

## 10. Production smoke (unauthenticated, after `dep-dc45f4m7bikc738719ig`)

| Check | Result |
| --- | --- |
| `GET /api/health` | **PASS** — `200 {"status":"UP","database":"UP"}`, 0.75 s |
| Four hardening headers present | **PASS** — `X-Frame-Options: DENY`, `nosniff`, `Referrer-Policy`, `Permissions-Policy` |
| `GET /api/admin/dashboard` anonymous | **PASS** — `401` |
| Malformed JSON to `/api/auth/login` | **PASS** — `400` |
| `GET /api/jobs?size=500` | **PASS** — `size` reported as 100 |
| `GET /api/jobs/1` | **PASS** — `Restorant Crew`, `moderationStatus: APPROVED` |
| `GET /employer/analytics.html` | **PASS** — `200` |
| Rate limiter active | **PASS (partial)** — `X-RateLimit-Limit: 20` and `X-RateLimit-Remaining` decrement per request on the live service; a `429` burst could **not** be reproduced from the release host because its outbound address changes between requests, so the budget was split across buckets. The `429` + `Retry-After` behaviour was reproduced on a local instance (21st attempt) and in unit tests |
| Forged `X-Forwarded-For` cannot reset the counter | **PASS** — verified locally after the fix: 21 attempts with a different forged first entry each time still ended in `429` |

## 11. Manual verification still required by an operator

These are **BLOCKED** for the release engineer because they need production credentials, the access code, or a data change:

1. Sign in to production as administrator with the real access code (or admin email/password) and open the dashboard.
2. Verify an employer, approve a job post, resolve a report, dismiss a suspicious flag — one cycle each.
3. Suspend and restore a test account (never a real user), confirming the session stops working immediately.
4. Confirm the seeded administrator password has been rotated.
5. Confirm Supabase backup retention and perform one restore rehearsal into a scratch project.
6. Sign in as a real employer and a real seeker to confirm both dashboards on the production origin.

Until those are done, the release is **verified locally and smoke-tested in production**, not fully production-accepted.

## 12. Release decision

| Question | Answer |
| --- | --- |
| Functionally complete for the documented scope? | Yes — all eight phases implemented and verified locally |
| Safe to demonstrate and evaluate? | Yes — smoke checks pass; rate limiting is live |
| Safe for public launch at scale? | Not yet — see `SECURITY_AND_LIMITATIONS.md` §3: rotate the seeded admin password, add a CSP, per-account throttling, HttpOnly cookies, disable public Swagger, server-side paging |
| Remaining blocker for academic submission? | None, provided the operator items in §11 are completed before the defense |
