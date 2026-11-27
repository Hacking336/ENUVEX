# Phase 9 — Final Acceptance Test Report

**Project:** ENUVEX Job & Skills Matching (JSM)
**Production:** https://enuvex.onrender.com
**Audited commit:** `b207ee0` (deployed) / `bad0b32` (docs record) — repository `Hacking336/ENUVEX`, `main`
**Audit date:** 2026-11-26, 22:55–23:20 UTC
**Scope:** audit only. No production data was created, changed or deleted. No password was reset. No secret is reproduced here. No deployment was made and nothing was pushed to `main`, because the Render service auto-deploys from `main` and a push would have triggered a deploy.

---

## 1. Executive summary

The system is **functionally complete and ready to demonstrate** for the academic defense. Today's audit re-exercised the parts that can be exercised without production credentials: the full automated suite (59 / 59 pass on PostgreSQL), twelve hand-calculated matching cases plus a determinism check, the geographic dataset (exactly 125 barangays with the reported split), and read-only production checks (health, 27 pages, security headers, anonymous-access refusals, validation, rate-limit headers, deploy history).

Two operational facts matter more than anything else for the defense:

1. **The free hosting tier sleeps.** The first request today took **63.5 s** to answer; every request after it answered in **≈0.5 s**. Warm the site up 2–3 minutes before the panel walks in, exactly as `LIVE_DEMO_GUIDE.md` already says.
2. **Authenticated production workflows (all three roles, including admin) were not re-run today.** They require production accounts, and admin moderation requires the real access code. They are covered by the earlier phase evidence (178 scripted HTTP assertions, browser checks) and remain **VERIFIED LOCALLY ONLY / prior phase**, not re-verified in production in this audit.

No defect was found in the code during this audit. One documentation-level nit and three operational risks are listed in §17–18. Nothing must be fixed before the defense; three items should be done operator-side.

---

## 2. Overall acceptance status

| Band | Meaning | Count |
| --- | --- | --- |
| **VERIFIED (today)** | Executed in this audit, evidence recorded below | 41 checks |
| **VERIFIED LOCALLY ONLY** | Real, executed in an earlier phase on the local instance/DB; not re-run today | ~180 HTTP assertions, browser checks |
| **VERIFIED IN PRODUCTION** | Read-only production request executed today | 14 checks |
| **NOT VERIFIED** | Cannot be verified without authorization/credentials/tooling | 9 areas |
| **FAILED** | Executed and failed | 0 |
| **BLOCKED** | Needs the admin access code or production account authorization | admin mutations, authenticated role flows in production |

**Recommendation:** **ACCEPTED for academic demonstration and presentation**, with the operator-side items in §21. Not yet "safe for public launch at scale" — that stance is unchanged from `SECURITY_AND_LIMITATIONS.md` §3.

---

## 3. Testing environment

| Item | Value |
| --- | --- |
| Audit machine | this cloud worktree, Linux, JDK 21.0.13+9, Maven 3.9.9 |
| Database (local) | PostgreSQL from the bundled distribution, port 55432, schema `enuv`, Flyway V1–V9 already applied, `ddl-auto=validate` |
| Unit/integration tests | `cd backend && mvn -o test` with `DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET` env overrides; finished 23:07Z |
| Production | Render service `ENUVEX` (`srv-dbk9hf3bc2fs73eqkuf0`), plan **free**, region Singapore, Docker, 1 instance, auto-deploy from `main`, `suspended: not_suspended` |
| Live deploy | `dep-dc45f4m7bikc738719ig` — commit `b207ee0` — `live` 2026-11-26T16:03:46Z |
| Production requests | `curl` only, unauthenticated, read-only or validation-triggering (malformed body, wrong method). No account, job, application or report was created or modified |

---

## 4. Test results summary

| # | Area | Level | Result |
| --- | --- | --- | --- |
| 1 | Job seeker workflow | VERIFIED LOCALLY ONLY (prior phases) + production pages | PASS with caveat |
| 2 | Employer workflow | VERIFIED LOCALLY ONLY (prior phases) + production pages | PASS with caveat |
| 3 | Admin workflow | VERIFIED LOCALLY ONLY (prior phase, 128 scripted checks) + production route guards | PASS with caveat |
| 4 | Matching engine | **VERIFIED today** — 59-test suite + 12 hand-calculated cases + determinism | PASS |
| 5 | Geographic mapping | **VERIFIED today** (dataset) + prior browser check | PASS |
| 6 | Application tracking | VERIFIED LOCALLY ONLY (prior suites) + source audit today | PASS |
| 7 | Analytics | VERIFIED LOCALLY ONLY (prior phase) + production 401 guard | PASS with caveat |
| 8 | Security | **VERIFIED today** (headers, guards, validation, rate-limit headers) + prior suite | PASS |
| 9 | Responsiveness | NOT VERIFIED in this audit (no browser session); prior phase evidence | NOT VERIFIED (prior PASS) |
| 10 | Deployment/infrastructure | **VERIFIED today** (health, deploy receipt, cold start, headers) | PASS with caveat |
| 11 | Documentation accuracy | **VERIFIED today** (targeted cross-checks) | PASS, 1 nit |
| 12 | Defense readiness | **VERIFIED today** (material and site behaviour) | READY |

---

## 5. Job seeker workflow

Level: **VERIFIED LOCALLY ONLY** for the authenticated flow (Phase 6/7 scripted suites and browser checks against the local instance). Every seeker page returns **200** in production today, but the actions behind them were not executed in production.

| Test case | Expected | Actual | Status | Evidence |
| --- | --- | --- | --- | --- |
| Seeker pages reachable (production) | 200 for every page | 200 × 9 (`jobs`, `dashboard`, `profile`, `skills`, `applications`, `application`, `notifications`, `reports`, `saved-jobs`) | VERIFIED IN PRODUCTION | curl, this audit |
| Registration (seeker, multipart with photo) | Account created, JWT returned | Passed | VERIFIED LOCALLY ONLY | `tools/phase7_security_check.py` 50/50; Phase 6 suites |
| Login / logout / session expiry | JWT accepted, 401 after expiry, logout clears token | Passed | VERIFIED LOCALLY ONLY | same suites; `RateLimiterTest`; `SecurityConfig` stateless JWT |
| Profile completion | Percentage from six weighted fields | Passed (`profileCompleteness`, 65/100 and 100/100 cases) | VERIFIED today (formula) / locally (UI) | `MatchingEngineTest` cases |
| Skills management, work experience, preferences | Saved and used by matching | Passed | VERIFIED LOCALLY ONLY | Phase 6 suites |
| Job search, filters, pagination | Results filtered, page size capped at 100 | `GET /api/jobs?size=100` → 200; `size=100` cap asserted in Phase 7 | VERIFIED IN PRODUCTION (cap) / locally (filters) | curl + Phase 7 suite |
| Geographic search, job map | Map renders with pins and counts | Page 200 in prod; rendering verified in Phase 6/8 browser check | VERIFIED LOCALLY ONLY (render) | `docs/presentation/screenshots/02-job-map.png` |
| Match percentage, recommendation, job detail | Deterministic score with explanation | 12 hand-calculated cases pass | VERIFIED today | §8 |
| Application submission, duplicate refusal | Created once; second attempt refused | Passed | VERIFIED LOCALLY ONLY | Phase 6 suites |
| Status tracking, withdrawal | Timeline shown; withdrawal terminal | Passed | VERIFIED LOCALLY ONLY | Phase 6 suites, source audit §10 |
| Notifications | Created on status change, read/read-all/delete | Passed | VERIFIED LOCALLY ONLY | Phase 6/7 suites |
| Skills gap, career guidance | Page renders with real data | Page 200 in prod; data verified in Phase 8 | VERIFIED LOCALLY ONLY (data) | `screenshots/04-skills-gap.png` |

**Not performed in production:** registration (would create a real account), login with a real seeker account, applying to a real job.

---

## 6. Employer workflow

Level: **VERIFIED LOCALLY ONLY** (prior phases) + production page availability and route guards.

| Test case | Expected | Actual | Status | Evidence |
| --- | --- | --- | --- | --- |
| Employer pages reachable | 200 | 200 × 7 (`jobs`, `post-job`, `applicants`, `applicant-profile`, `dashboard`, `analytics`, `profile`) + `register` | VERIFIED IN PRODUCTION | curl, this audit |
| Registration, login, profile, company info | Created, editable | Passed | VERIFIED LOCALLY ONLY | Phase 6/7 suites |
| Verification status shown to employer | Status visible, employer cannot self-verify | Passed | VERIFIED LOCALLY ONLY | `ModerationAuthorizationTest` (6 tests) |
| Job creation / editing / requirements / skills / salary / location | Post saves, moderation status assigned | Passed | VERIFIED LOCALLY ONLY | Phase 6 suites (36/36 employer-facing states) |
| Barangay + municipality selection | Values persisted against the 125-barangay dataset | Passed | VERIFIED LOCALLY ONLY | Phase 6 suite; dataset verified today (§9) |
| Approval workflow, publication | Pending until approved; approved posts appear publicly | Production job list shows an `APPROVED` post | VERIFIED IN PRODUCTION (publication) / locally (workflow) | `GET /api/jobs` returns `Restorant Crew` |
| Applicant list, review, shortlist, interview, status update | All transitions applied and notified | Passed | VERIFIED LOCALLY ONLY | Phase 6 suites |
| Analytics + CSV export | Nine sections, real numbers, guarded CSV | Passed in Phase 7 (page had been broken; fixed) | VERIFIED LOCALLY ONLY | Phase 7 commit `81e31f0`; page 200 in prod |
| Ownership enforcement (IDOR) | Employer A cannot read/modify B's jobs, applicants, analytics | Cross-tenant reads refused (403/404) | VERIFIED LOCALLY ONLY | `phase7_security_check.py` IDOR block |

**Not performed in production:** creating or editing a job, approving, scheduling an interview, exporting a real employer's CSV.

---

## 7. Admin workflow

Level: **VERIFIED LOCALLY ONLY** for mutations (prior phase: 128 scripted checks across three suites); **VERIFIED IN PRODUCTION** for the access guards, which is what matters most here.

| Test case | Expected | Actual | Status | Evidence |
| --- | --- | --- | --- | --- |
| Admin authentication | Access code required; refused when unset | Code path present in `AdminController`/`SecurityConfig` (Phase 7 `81e31f0`) | VERIFIED LOCALLY ONLY | Phase 7 suite |
| Admin API without a token (production) | 401 | `GET /api/admin/analytics/overview` → **401** | VERIFIED IN PRODUCTION | curl |
| Admin pages reachable | 200 | 200 × 11 | VERIFIED IN PRODUCTION | curl |
| Dashboard metrics | Real counts from the database | Passed | VERIFIED LOCALLY ONLY | Phase 6 suite |
| Employer verification, job moderation, reports | State changes with reasons, audit writes | Passed (92/92) | VERIFIED LOCALLY ONLY | `phase6_moderation_check.py` |
| Suspicious listing detection / dismissal | 5 rule families, dismissal stops re-flagging | 12 unit tests pass today | VERIFIED today (unit) / locally (API) | `SuspiciousListingServiceTest` |
| Account suspension / restoration | Refused for self and last active admin; session dies | Guards present in `AdminController` lines 585–605; suspension-kills-session asserted | VERIFIED today (source) / locally (behaviour) | source audit + Phase 7 suite |
| Audit logs | Append-only, admin-only | Passed | VERIFIED LOCALLY ONLY | `ModerationAuthorizationTest` |
| Admin notifications | Created on moderation events | Passed | VERIFIED LOCALLY ONLY | Phase 6 suite |
| Unauthorised access (seeker/employer → admin) | Refused | Passed | VERIFIED LOCALLY ONLY | Phase 7 suite |

**BLOCKED:** any authenticated admin action in production — the real `JSM_ADMIN_ACCESS_CODE` was not used, and using it is an operator decision (documented procedure in `RELEASE_CHECKLIST.md` §11).

---

## 8. Matching engine validation

Level: **VERIFIED today**. Source read, weights confirmed, 12 cases hand-calculated on paper before execution, determinism checked, and the whole 25-test `MatchingEngineTest` plus the other 34 tests pass.

Weights in `MatchingEngine.java` lines 43–47: `WEIGHT_SKILLS 40.0`, `WEIGHT_LOCATION 25.0`, `WEIGHT_EMPLOYMENT_TYPE 15.0`, `WEIGHT_EXPERIENCE 10.0`, `WEIGHT_SALARY 10.0`. Formula: `overall = earned / applicable × 100`, clamped, one decimal; a criterion with no data is removed from **both** sides. Bands: ≥90 Excellent, ≥75 Strong, ≥60 Good, ≥40 Partial, else Low. No model, no randomness, no education or certification term in the score (education appears only in profile completeness and as a job requirement display).

| # | Case | Hand calculation | Engine output | Status |
| --- | --- | --- | --- | --- |
| 1 | Everything matches | 40 + 20 + 15 + 10 + 10 = 95 / 100 | 95.0 Excellent | PASS |
| 2 | 2 of 3 skills | 26.7 + 20 + 15 + 10 + 10 = 81.7 | 81.7 Strong | PASS |
| 3 | 0 of 3 skills | 0 + 20 + 15 + 10 + 10 = 55 | 55.0 Partial | PASS |
| 4 | Same barangay (case-insensitive) | 40 + 25 + 15 + 10 + 10 = 100 | 100.0 Excellent | PASS |
| 5 | Bordering municipality (Labo → Daet) | 12.5 instead of 20 → 87.5 | 87.5 Strong | PASS |
| 6 | Non-adjacent (Basud → Labo) | 2.5 → 77.5 | 77.5 Strong | PASS |
| 7 | Job lists no skills | applicable 60, earned 55 → 91.7, `complete=false` | 91.7 Excellent | PASS |
| 8 | No salary and no employment preference | applicable 75, earned 70 → 93.3 | 93.3 Excellent | PASS |
| 9 | 1 year against 2-year minimum | 5 instead of 10 → 90.0 | 90.0 Excellent | PASS |
| 10 | Job max below expected min | salary 0 → 40+20+15+10+0 = 85 | 85.0 Strong | PASS |
| 11 | Full-time preference, temporary job | employment 0 → 40+20+0+10+10 = 80 | 80.0 Strong | PASS |
| 12 | Partial salary overlap (50 % of the offered span) | 50 + 25 = 75 % of 10 = 7.5 → 92.5 | 92.5 Excellent | PASS |

Case 11 initially failed **my** arithmetic (I had written 85 by mistake); the engine's 80 was correct, the expectation was corrected and the case then passed. That is an audit-script error, not a product defect, and it is recorded rather than hidden.

Determinism: the same profile and post scored **95.0** on 50 freshly constructed engine instances — no state, no caching, no randomness.

**Reproducibility across interfaces:** all three consumers (seeker list, employer applicant list, admin statistics) call the same `MatchingEngine.calculateMatch`; no separate scoring path exists. Verified by source inspection today, and by the Phase 7 cross-surface checks.

---

## 9. Geographic mapping validation

Level: **VERIFIED today** (dataset) / **VERIFIED LOCALLY ONLY** (rendering).

`backend/src/main/resources/geo/barangays.geojson` — 125 features, counted today:

| Municipality | Reported | Counted in file | Status |
| --- | --- | --- | --- |
| Basud | 29 | 29 | PASS |
| Daet | 25 | 25 | PASS |
| Labo | 52 | 52 | PASS |
| Vinzons | 19 | 19 | PASS |
| **Total** | **125** | **125** | **PASS** |

Each feature carries `code`, `name`, `municipalityCode`, `municipality`, and polygon geometry; the codes follow the PSA/PSGC pattern (`0501601001` = Angas, Basud), which is the authoritative source of the names and boundaries. Boundary accuracy was **not** independently surveyed; the polygons are the PSA/PSGC shapes, not a hand-drawn approximation. Barangay selection in forms is limited to the chosen municipality; the map page returns 200 in production, and marker/count/near-me behaviour was verified in the Phase 6/8 browser sessions (`screenshots/02-job-map.png`).

Not verified today: map interaction, near-me geolocation, radius search, mobile map usability (§13).

---

## 10. Application tracking validation

Level: **VERIFIED LOCALLY ONLY** (Phase 6 suites) + **source audit today**.

Legal transitions in `ApplicationService` (static map, lines 36–52):

| From | Allowed to |
| --- | --- |
| SUBMITTED | REVIEWING, SHORTLISTED, REJECTED |
| REVIEWING | SUBMITTED, SHORTLISTED, INTERVIEW, REJECTED |
| SHORTLISTED | REVIEWING, INTERVIEW, ACCEPTED, REJECTED |
| INTERVIEW | SHORTLISTED, ACCEPTED, REJECTED |
| ACCEPTED | SHORTLISTED, REJECTED |
| REJECTED | REVIEWING, SHORTLISTED |
| WITHDRAWN | — (terminal) |

- Invalid transitions are rejected (Phase 6 suite; 36/36 employer-facing assertions).
- Every change writes an `ApplicationStatusHistory` row with actor and role — the timeline the seeker and employer both read.
- Interview scheduling, rescheduling, cancellation covered by the same suite.
- Withdrawal sets `WITHDRAWN`, notifies both sides, and is terminal: `canManage` is false afterwards, and no further transition is accepted.
- Duplicate application refused by the service; withdrawn-then-reapply follows the duplicate rule (refused) — this is the intended behaviour and is documented in the user manual.
- Ownership: seeker sees only own applications, employer only applications to own posts (Phase 7 IDOR block).

---

## 11. Analytics validation

Level: **VERIFIED LOCALLY ONLY** (Phase 7, against the local database). Production returns **401** for anonymous analytics requests and **200** for the employer analytics page shell.

Phase 7 evidence: nine sections returned numbers matching the database on the local instance (21 posts, 1 applicant at the time), CSV export built server-side with a formula-injection guard, and the page was verified at five widths. What was **not** re-done today: comparing production analytics numbers against production rows (needs an authenticated employer), and skills-demand/geographic-distribution counts against a fresh query.

---

## 12. Security validation

Level: **VERIFIED IN PRODUCTION today** for the unauthenticated surface; **VERIFIED LOCALLY ONLY** for authenticated behaviour.

| Test | Expected | Actual (production, 2026-11-26) | Status |
| --- | --- | --- | --- |
| Admin API without token | 401 | 401 | VERIFIED IN PRODUCTION |
| `/api/applications/my` without token | 401 | 401 | VERIFIED IN PRODUCTION |
| `/actuator/env` | 401 (not exposed) | 401 | VERIFIED IN PRODUCTION |
| Malformed JSON body | 400, no stack trace | 400 | VERIFIED IN PRODUCTION |
| Unknown path `/nonexistent-xyz` | 404 | **401** (empty response) | PASS-with-scope — see §17 nit 1 |
| Security headers | HSTS, X-Frame-Options, nosniff, Referrer-Policy, Permissions-Policy | all five present; `cache-control: no-cache, no-store` | VERIFIED IN PRODUCTION |
| Rate limiting | headers present, counters persist | `x-ratelimit-limit: 20`, `x-ratelimit-remaining: 18` on `/api/auth/login` | VERIFIED IN PRODUCTION (headers) / locally (429 after burst) |
| Session handling | stateless JWT, suspended account refused per request | source: `SessionCreationPolicy.STATELESS`, `frameOptions.deny()`, suspension re-check in the JWT filter | VERIFIED today (source) / locally (behaviour) |
| CSRF | disabled, justified by stateless bearer tokens and no cookie auth | `csrf(AbstractHttpConfigurer::disable)` | PASS (design) |
| SQL injection | bound parameters only | repositories use JPA/SQL parameters; asserted in Phase 7 suite | VERIFIED LOCALLY ONLY |
| XSS | escaped output | analytics table text escaped (Phase 7 fix); other pages render via escaped templates/`textContent` | VERIFIED LOCALLY ONLY |
| IDOR | cross-tenant refused | Phase 7 suite, 50/50 | VERIFIED LOCALLY ONLY |
| Suspended accounts | cannot sign in and cannot continue an existing session | Phase 7 suite | VERIFIED LOCALLY ONLY |
| JWT expiry | expired token refused | Phase 7 suite | VERIFIED LOCALLY ONLY |
| API documentation exposure | public (documented limitation) | `/swagger-ui/index.html` 200, `/api-docs` 200 | VERIFIED IN PRODUCTION — **documented** in `SECURITY_AND_LIMITATIONS.md` §5 |
| Secrets in deliverables | none | pattern scan clean (Phase 8), no secret reproduced here | VERIFIED today (no new secret introduced) |

---

## 13. Responsiveness validation

Level: **NOT VERIFIED in this audit.** No browser session was opened and no viewport was measured today; the report does not claim otherwise.

Prior evidence (Phase 7/8, real browser): the analytics console measured **0 px horizontal overflow** at 1440 / 1024 / 768 / 390 / 360 px, with the wide table scrolling inside `.table-wrap`; the sidebar collapses to bottom navigation on small screens; eight role pages rendered at 1440 px with no console errors; one accessibility finding (a job `<select>` without an accessible name) was fixed with `aria-label`. Keyboard-only navigation and colour-contrast measurement remain **NOT TESTED** — unchanged from `TESTING_AND_QA_REPORT.md` §5.

---

## 14. Deployment validation

Level: **VERIFIED IN PRODUCTION today.**

| Check | Result |
| --- | --- |
| Service state | `not_suspended`; plan `free`; 1 instance; Docker; auto-deploy `main`; region Singapore |
| Deployed commit | `dep-dc45f4m7bikc738719ig` = `b207ee0`, `live` 16:03:46Z (previous deploy `456ddd6` deactivated) |
| Health | `200 {"status":"UP","database":"UP"}` |
| Cold start | **63.5 s** for the first request today; then 0.54 s, 0.55 s, 0.54 s |
| Migration | V9 applied by Flyway on start-up (Phase 8); schema validated by `ddl-auto=validate` in today's context test |
| Frontend/backend integration | 27 static pages 200, JSON API answering, no mixed-content or CORS error in the responses inspected |
| Logging | Render log stream available; no error shown for the audited requests (log inspection limited to the deploy record) |
| Rate-limit persistence | counters live in PostgreSQL (V9 table), so they survive restarts and are shared by instances |
| Backup | Supabase automated backup retention **not inspected today**; no restore has ever been rehearsed — operator item |
| Recovery | Restart = new container from the same image; Render restart/Docker redeploy documented; no data loss risk beyond the database provider's own backups |

The service is single-instance, so "restart behaviour" is not a distributed-consistency question; the only stateful dependency is the managed PostgreSQL database.

---

## 15. Documentation audit

Level: **VERIFIED today** for the items checked; not an exhaustive line-by-line rewrite.

| Claim checked | Source of truth | Verdict |
| --- | --- | --- |
| Weights 40/25/15/10/10 | `MatchingEngine.java` | accurate |
| Renormalisation with missing criteria | `calculateMatch` | accurate, with worked example |
| Barangay counts 29/25/52/19 = 125 | `barangays.geojson` | accurate |
| Swagger UI and `/api-docs` public | live production | accurately disclosed as limitation #5 |
| Free tier sleeps, first request slower | live production (63.5 s) | accurate; demo guide already tells the presenter to warm up |
| Health payload | live production | accurate |
| Admin access-code requirement | `SecurityConfig` / Phase 7 | accurate |
| Status transition table | `ApplicationService` | accurate |
| Last-admin and self-suspension guards | `AdminController` | accurate (present) |
| API and deployment paths | spot check of the pages and endpoints | accurate |

**Necessary corrections found: one.** `API_DOCUMENTATION.md` says Swagger UI is at `/swagger-ui.html`; that path **302-redirects** to `/swagger-ui/index.html`, which is what returns 200. The JSON is at `/api-docs` (200). Not wrong, but a reader following it literally sees a redirect — worth a one-line clarification, not a rewrite.

Screenshots in `docs/presentation/screenshots/` are dated 2026-11-26 15:08, i.e. before the `b207ee0` deploy. That commit changed **server-side rate limiting only**; no page markup, CSS or JavaScript changed, so the screenshots still match the running UI. They were not re-captured today.

---

## 16. Defense readiness

**Ready.** Supporting material is complete and internally consistent: 16-slide deck with speaker notes, presenter script, 32 Q&A entries, 16-step live demo guide, three role manuals, and ten release documents.

Safe to demonstrate live:

- Public site, registration form (use a throwaway account or the prepared one), sign-in for seeker and employer.
- Job search, filters, pagination, job detail with a match explanation.
- Job map with barangay boundaries and pins.
- Seeker skills gap page, applications list, notifications.
- Employer dashboard, applicants, analytics and CSV export.
- Matching arithmetic explained on a whiteboard using §8 cases 1–3.

Needs prepared accounts or warm-up:

- Any authenticated flow — warm the host **2–3 minutes early** (63.5 s cold start today).
- Admin console — needs the access code held by the operator; keep it out of slides and out of the recording.
- Employer analytics — needs at least one applicant on the employer's post or the funnel looks empty.

External dependencies and failure modes:

- Render free tier: sleep, single instance, no SLA.
- Supabase Postgres: `/api/health` reports the real database state (`{"status":"UP","database":"UP"}` today), so an outage is visible rather than hidden.
- Map tiles come from a public tile provider; if the venue has no internet, use the map screenshot.

Backup material that should be on the presenter's machine: screenshots 01–08, the deck itself exported to PDF, and a short screen recording of one full application submission → status change → notification.

Likely panel questions and where the answers are: `DEFENSE_QUESTIONS_AND_ANSWERS.md` (32 entries) covers the matching formula, the absence of AI/ML, barangay data provenance, security model, testing scope, and the honest limitations.

---

## 17. Defects identified

**Product defects found in this audit: none.**

Two observations, neither requiring a code change before the defense:

1. **Unknown paths return 401, not 404** (`/nonexistent-xyz`). Spring Security authenticates before routing, so an unknown route answers "unauthorised". No information is leaked; it is a cosmetic deviation from REST convention.
2. **`API_DOCUMENTATION.md` redirect nit** — `/swagger-ui.html` 302 → `/swagger-ui/index.html`. One-line documentation clarification.

One audit-script error (my own arithmetic in matching case 11) is recorded in §8 for transparency; it was corrected in the audit script, not in the product.

---

## 18. Risks and limitations

| # | Risk | Impact | Mitigation in place |
| --- | --- | --- | --- |
| 1 | Free tier sleeps (63.5 s cold start measured today) | Live demo stalls if opened cold | Demo guide: warm up 2–3 min early; screenshots as backup |
| 2 | Single instance, no uptime SLA | Site down if Render has an incident | Local instance can be run from the repo as fallback |
| 3 | Seeded/admin credentials not rotated | A known credential could be reused | Operator task, documented in the release checklist |
| 4 | No rehearsed database restore | Recovery time unknown | Supabase automated backups; rehearsal listed as operator task |
| 5 | Public Swagger UI | API shape discoverable | Disclosed limitation; can be disabled by config |
| 6 | No CSP, token in `localStorage` | XSS impact would be larger than needed | Escaping verified; CSP listed as future work |
| 7 | Production job list shows demo-named data ("Test Company", "Restorant Crew") | Looks unfinished during a walkthrough | Use the employer/seeker demo path, or present seeded data as sample data |
| 8 | No load test, no independent security audit, no user-acceptance study | Cannot claim performance or satisfaction numbers | Explicitly not claimed anywhere in the documents |
| 9 | Rate-limit 429 burst not reproduced on the live host | Throttling proven locally, not in production | Local proof: 21st attempt → 429 with `Retry-After: 42`; spoof-resistant key |

---

## 19. Production tests not performed

| Area | Why not | How to do it, if authorised |
| --- | --- | --- |
| Registration and sign-in with real production accounts | Would create/modify production data (rule 1–2) | Use a designated test account, or run against the local instance |
| Seeker actions in production (apply, withdraw, save) | Mutates production data | Same |
| Employer actions in production (post, edit, shortlist, schedule) | Mutates production data | Same |
| Admin console authenticated | Needs the real access code, an operator decision | Read `JSM_ADMIN_ACCESS_CODE` from Render → Environment, sign in, follow `USER_MANUAL_ADMIN.md` |
| Analytics figures vs production rows | Needs an authenticated employer | Sign in as the demo employer and compare with `GET /api/employer/analytics/*` |
| Rate-limit 429 burst on the live host | Each request egresses from a different address, splitting the bucket | Run the burst from one fixed address |
| Responsiveness at 360–1920 px | No browser session opened today | Open the deployed site with devtools at each width |
| Keyboard navigation, contrast measurement | No tooling in this environment | Manual pass or axe/Lighthouse |
| Backup restore rehearsal | Destructive on a managed database | Restore into a separate Supabase project |
| Load/concurrency behaviour | No load tooling authorised | k6/Locust against a staging instance |

---

## 20. Recommended final demo sequence

1. **T−3 min:** open https://enuvex.onrender.com, wait for the landing page (first request wakes the host), then leave the tab open.
2. Landing page → purpose of JSM, the four municipalities, the two user roles.
3. Seeker sign-in → job search → filter by municipality and employment type.
4. Open a job → point at the match percentage → explain the five weights and the per-criterion sentences.
5. Job map → barangay boundaries, pins, "near me".
6. Skills gap page → a missing skill, its demand evidence, the simulation result.
7. Submit an application → show the confirmation and the tracker entry.
8. Switch to the employer → applicants list → shortlist → schedule an interview (this is the visible status-change + notification moment).
9. Employer analytics → funnel and CSV export.
10. Switch to the admin → dashboard, a pending employer verification (approve only if the operator authorises it), audit log.
11. Close on the architecture slide → deterministic engine, PostgreSQL, JWT security, documented limits.

Fallbacks: screenshots for the map and analytics, a recording for the application flow, and the local instance if Render is unavailable.

---

## 21. Final acceptance recommendation

**ACCEPTED — ready for academic presentation, demonstration and final acceptance.**

- The build compiles and the complete automated suite passes today: **59 tests, 0 failures**, against a real PostgreSQL with all nine migrations and `ddl-auto=validate`.
- The matching engine matches its documentation exactly, on twelve independently hand-calculated cases, deterministically.
- The geographic claim (125 barangays, 29/25/52/19) is exact.
- Production is live on the audited commit, healthy, correctly hardened on its unauthenticated surface, and serves all 27 pages.
- No defect was found. Nothing must be fixed before the defense.

Before the defense, operator-side (minutes, not days):

1. Warm the production site 2–3 minutes before the session (63.5 s cold start).
2. Decide who holds the admin access code and whether to demonstrate an admin action live.
3. Rotate the seeded admin password (listed since Phase 7; still outstanding).
4. Optional: capture fresh screenshots and add the `/swagger-ui.html` redirect note.

Honest scope statement: authenticated production workflows, live responsiveness measurement, accessibility measurement, backup restore and load behaviour were **not** verified in this audit. They are supported by earlier local evidence and, where applicable, disclosed as limitations in the release documents — not claimed as production-verified here.
