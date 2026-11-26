# API Documentation

**JSM — final release.** Paths, roles and parameters below were read from the controllers in `backend/src/main/java/com/jobskills/controller`. Base URL: `/api` on the same host as the pages. All bodies are JSON unless stated. Authentication is `Authorization: Bearer <token>`.

Common status codes: `200` OK, `201` created, `400` invalid input, `401` missing/invalid token, `403` wrong role or not the owner, `404` unknown resource, `405` wrong method, `409` conflict, `429` rate limited, `500` unexpected.

---

## 1. Authentication — `/api/auth` (public)

| Method | Path | Purpose | Body / params | Response |
| --- | --- | --- | --- | --- |
| POST | `/login` | Sign in | `{ "email": "...", "password": "..." }` | `200 { token, userId, userType, email, name }` |
| POST | `/register` | Register a seeker or employer | `multipart/form-data`: part `profile` (JSON `RegisterRequest`), optional `profilePhoto`, `logo` | `201` same shape as login |

`RegisterRequest` fields: `email`, `password`, `userType` (`JOB_SEEKER`/`EMPLOYER`), plus profile fields (`fullName`, `municipality`, `barangay`, `contactNumber`, `skills`, `educationLevel`, `workExperience`, … for seekers; `businessName`, `employerName`, `businessType`, `businessAddress`, … for employers).

Example:

```bash
curl -X POST https://enuvex.onrender.com/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"seeker@example.com","password":"<password>"}'
```

```json
{ "token": "<jwt-token>", "userId": 2, "userType": "JOB_SEEKER",
  "email": "seeker@example.com", "name": "Test Job Seeker" }
```

Administrator note: `email` may be `admin` or `admin++`, in which case the `password` field must contain the deployment's `JSM_ADMIN_ACCESS_CODE`; without that variable the shortcut is refused.

Rate limit: 20 requests per minute per client address (`429` afterwards).

## 2. Public lookups

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/health` | Readiness: `{ "status": "UP", "database": "UP" }`, `503` when the database is unreachable |
| GET | `/api/municipalities` | Municipality enum values |
| GET | `/api/categories` | Job category enum values |
| GET | `/api/business-types` | Business type enum values |

## 3. Jobs and search (public reads)

| Method | Path | Purpose | Parameters |
| --- | --- | --- | --- |
| GET | `/api/jobs` | Approved, active posts with filters | `municipality`, `barangay`, `category`, `skills`, `minSalary`, `maxSalary`, `employmentType`, `workSchedule`, `maxDistance`, `nearLatitude`, `nearLongitude`, `radiusKm`, `experience`, `education`, `matchBand`, `minMatchScore`, `page` (default 0), `size` (default 20, **max 100**), `sortBy` (`createdAt` default), `sortDir` (`DESC` default) |
| GET | `/api/jobs/{id}` | One job detail | `id` |
| GET | `/api/jobs/map` | Posts with confirmed coordinates for the map | same filters as `/api/jobs` |
| GET | `/api/jobs/with-match` | Same list, each item carrying the caller's match | same filters; token optional (score only for a signed-in seeker) |
| GET | `/api/search/jobs` | Equivalent search route used by the global search box | `keyword`, pagination |

```json
{ "content": [ { "id": 1, "jobTitle": "Legacy Cashier", "employerName": "Legacy Verified Co",
  "municipality": "DAET", "barangay": "Borabod", "employmentType": "FULL_TIME",
  "moderationStatus": "APPROVED", "matchPercentage": 80.0, "matchedSkills": [],
  "missingSkills": [], "createdAt": "2026-11-26T01:03:18.391845" } ],
  "totalElements": 23, "totalPages": 2, "size": 20, "number": 0 }
```

## 4. Geographic mapping — `/api/geo` (public GETs)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/geo/coverage` | Coverage statement and the four municipalities with barangay counts |
| GET | `/api/geo/map-config` | Tile URL, attribution, zoom limits and centre (from environment) |
| GET | `/api/geo/municipalities` | Municipalities with PSGC codes |
| GET | `/api/geo/municipalities/{code}/barangays` | Barangays of a municipality |
| GET | `/api/geo/job-counts` | Active job counts by municipality/barangay |
| GET | `/api/geo/boundaries?codes=…` | GeoJSON polygons for selected municipality/barangay codes |

## 5. Job seeker — `/api/job-seekers` (role `JOB_SEEKER`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/profile` | Own profile |
| PUT | `/profile` | Update own profile |
| GET | `/profile/completion` | Simple completion percentage |
| GET | `/profile/completeness` | Matching-relevant completeness with the missing fields and links |
| GET | `/jobs` | Job search scoped to the seeker, with match |
| GET | `/jobs/map` | Map data for the seeker's filters |
| GET | `/recommended` | Active posts ordered by match score |
| GET | `/jobs/{id}` | Job detail |
| GET | `/jobs/{id}/match` | `MatchResultDto` for this job |
| POST | `/apply?jobId={id}` | Apply (one application per post; `400` if already applied) |
| GET | `/applications` | Own applications |
| GET | `/skills-analysis` | Skills gap analysis |
| POST | `/skills-simulation` | Simulate adding skills (`{ skillNames: [...] }`) |
| GET | `/career-opportunities` | Career groups and local demand |
| GET/POST/DELETE | `/learning-interests` | Recorded learning interests |
| POST | `/saved?jobId={id}` | Save a job |
| GET | `/saved` | Saved jobs |
| DELETE | `/saved?jobId={id}` | Remove a saved job |

Match response (trimmed):

```json
{ "overallMatch": 80.0, "classification": "Strong Match", "applicableWeight": 65.0,
  "earnedPoints": 52.0, "complete": false,
  "criteria": [ { "key": "skills", "label": "Skills", "weight": 40.0, "applicable": false,
                  "earnedPoints": 0.0, "score": 0.0, "status": "NONE",
                  "detail": "This employer did not list required skills, so skills are left out of the score." } ],
  "matchedSkills": [], "missingSkills": [], "totalRequiredSkills": 0 }
```

## 6. Applications, interviews, tracking — `/api/applications` (signed in)

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| GET | `/api/applications` | seeker | Own applications |
| GET | `/api/applications/{id}` | owner | Application detail (job, employer, status, timeline summary) |
| GET | `/api/applications/{id}/timeline` | owner | Status and event history |
| GET | `/api/applications/{id}/interview` | owner | Interview record, if scheduled |
| PATCH | `/api/applications/{id}/status` | `EMPLOYER` (owns the post) | Change status; body `{ "status": "SHORTLISTED", "message": "…" }` |
| POST | `/api/applications/{id}/withdraw` | `JOB_SEEKER` | Withdraw |
| POST | `/api/applications/{id}/interview` | `EMPLOYER` | Schedule; `{ interviewAt, interviewType, location, meetingLink, instructions, employerMessage }` |
| PATCH | `/api/applications/{id}/interview` | `EMPLOYER` | Reschedule / update |
| DELETE | `/api/applications/{id}/interview` | `EMPLOYER` | Cancel |
| GET | `/api/applications/job/{jobId}/applicants` | `EMPLOYER` (owns the post) | Ranking by match score |

Statuses: `SUBMITTED → REVIEWING → SHORTLISTED → INTERVIEW → ACCEPTED | REJECTED`, plus `WITHDRAWN`. Every change writes a history row and notifies the other party.

## 7. Notifications — `/api/notifications` (signed in)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/notifications` | Own inbox (newest first) |
| GET | `/api/notifications/unread-count` | `{ "unread": n }` |
| PATCH | `/api/notifications/{id}/read` | Mark one read |
| PATCH | `/api/notifications/read-all` | Mark all read |
| DELETE | `/api/notifications/{id}` | Delete one |

Types emitted by `NotificationService`: `APPLICATION_SUBMITTED`, `APPLICATION_RECEIVED`, `APPLICATION_STATUS`, `APPLICATION_WITHDRAWN`, `APPLICANT_WITHDRAWN`, `INTERVIEW_INVITE`, `INTERVIEW_RESCHEDULED`, `INTERVIEW_CANCELLED`, `EMPLOYER_VERIFICATION`, `EMPLOYER_VERIFIED`, `EMPLOYER_REJECTED`, `VERIFICATION_INFO_REQUESTED`, `JOB_POST_APPROVED`, `JOB_POST_REJECTED`, `JOB_POST_CHANGES_REQUESTED`, `REPORT_RESOLVED`, `SUSPICIOUS_LISTING_FLAGGED`, `ACCOUNT_SUSPENDED`, `ACCOUNT_RESTORED`, `JOB_RECOMMENDATION`.

## 8. Employer — `/api/employers` (role `EMPLOYER`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET / PUT | `/profile` | Read / update company profile |
| POST | `/verification-request` | Ask for verification (sets `PENDING`) |
| POST | `/jobs` | Create a post → `PENDING_REVIEW` |
| PUT | `/jobs/{id}` | Edit own post (an edited approved post returns to review) |
| DELETE | `/jobs/{id}` | Delete own post |
| GET | `/my-jobs` | Own posts with moderation status |
| GET | `/dashboard` | Counters: posts, active posts, applicants, new applicants, interviews, accepted, rejected |

### Employer analytics (role `EMPLOYER`, own data only)

| Method | Path | Purpose | Parameters |
| --- | --- | --- | --- |
| GET | `/analytics/overview` | Headline numbers | `dateRange` (`7d`, `30d`, `90d`, `all`) |
| GET | `/analytics/job-performance` | Per-post table (paged) | `page`, `size`, `sortBy`, `sortDir`, `municipality`, `employmentType`, `statusFilter`, `dateRange` |
| GET | `/analytics/funnel` | Applied → reviewed → shortlisted → interview → accepted | `dateRange` |
| GET | `/analytics/response-metrics` | Time to first review, response rate, unanswered applications | `dateRange` |
| GET | `/analytics/matching` | Match distribution, averages, bands | `dateRange` |
| GET | `/analytics/skills/{jobId}` | Skill supply vs demand for one own post | `dateRange` |
| GET | `/analytics/geographic` | Applicant origin by municipality/barangay | `dateRange` |
| GET | `/analytics/insights` | Plain-language recruitment insights | `dateRange` |
| GET | `/analytics/export` | Everything as one JSON document; the page converts it to CSV client-side | `dateRange`, `format` |

`dateRange` accepts `7d`, `30d`, `90d` and `all` (default `30d` where a default applies).

## 9. Profiles — `/api/profiles` (signed in)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/job-seeker/completion` | Completion percentage |
| PUT | `/job-seeker` | Update seeker profile |
| PUT | `/employer` | Update employer profile |

## 10. Dashboard — `/api/job-seekers/dashboard`, `/api/employers/dashboard`

Counters per role (applications by status, saved jobs, recommended count; posts, applicants, interviews). Same auth rules as the rest of the role API.

## 11. Skills — `/api/skills` (signed in)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/skills/catalog?q=` | Skill catalogue used by the profile editors and simulation |
| GET | `/api/skills/resources?skill=` | Learning resources for a skill |

## 12. Reports and moderation — user side

| Method | Path | Role | Purpose |
| --- | --- | --- | --- |
| POST | `/api/moderation/reports` | signed in | Submit a report; body `{ targetType, targetId, category, description }` |
| GET | `/api/moderation/reports/mine` | signed in | Own reports |

`targetType`: `JOB_POSTING`, `EMPLOYER`, `USER`. `category`: `FRAUD_OR_SCAM`, `MISLEADING_INFORMATION`, `FAKE_EMPLOYER`, `DUPLICATE_LISTING`, `WRONG_LOCATION`, `INAPPROPRIATE_CONTENT`, `OTHER`. The reporter is always taken from the token, never from the body. Rate limit: 10 per hour per address.

## 13. Administrator — `/api/admin` (role `ADMIN`)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/dashboard` | Platform counters |
| GET | `/moderation-overview` | Pending employers, posts, reports, open flags |
| GET | `/employers`, `/employers/pending`, `/employers/{id}` | Employer list / queue / detail |
| PUT | `/employers/{id}/verify` | Verify (reason optional) |
| PUT | `/employers/{id}/reject-verification` | Reject (reason required) |
| PUT | `/employers/{id}/info-request` | Ask for more information (message required) |
| GET | `/job-posts` | All posts, paged (`page`, `size` ≤ 100, `status`, `search`) |
| GET | `/job-posts/pending-review`, `/job-posts/{id}` | Review queue / detail |
| PUT | `/job-posts/{id}/approve` | Approve |
| PUT | `/job-posts/{id}/reject` | Reject (reason required) |
| PUT | `/job-posts/{id}/changes-requested` | Request corrections (reason required) |
| PUT | `/job-posts/{id}/restore` | Restore a rejected/closed post |
| DELETE | `/job-posts/{id}` | Delete a post |
| GET | `/moderation-reports?status=` | Report queue |
| PUT | `/moderation-reports/{id}/review` | Mark under review |
| PUT | `/moderation-reports/{id}/resolve` | Resolve with notes |
| PUT | `/moderation-reports/{id}/dismiss` | Dismiss as invalid/duplicate |
| GET | `/moderation-reports/stats` | Report statistics |
| GET | `/suspicious-listings?status=` | Flagged listings |
| POST | `/suspicious-listings/detect` | Re-run detection; returns `{ flagged, rules }` |
| PUT | `/suspicious-listings/{id}/dismiss` | Dismiss a flag (reason required) |
| GET | `/users` | All accounts with role and state |
| PUT | `/users/{id}/suspend` | Suspend (reason required; cannot suspend self or the last admin) |
| PUT | `/users/{id}/restore` | Restore |
| GET | `/audit-logs?actionType=&targetType=&page=&size=` | Audit trail (read-only) |
| GET/POST/PUT/DELETE | `/learning-resources[/{id}]` | Manage learning resources |
| GET | `/skills-analytics`, `/skills-demand` | Demand vs supply |
| GET | `/applications` | All applications (platform view) |
| GET | `/reports` | Report summary object |

Related admin-only routes: `GET /api/admin/geo/stats`, `GET /api/admin/matching/stats`, `GET /api/reports/summary`.

Every moderation decision writes an `audit_logs` row with the previous and new state, and notifies the affected user.

## 14. Files

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/uploads/{filename}` | Serve a stored profile picture or logo (public, cache-friendly) |

Uploads are created only through registration (`multipart/form-data`), capped at 5 MB, stored in PostgreSQL.

## 15. Rate limiting

| Route | Default limit | Window | Scope |
| --- | --- | --- | --- |
| `POST /api/auth/login` | 20 | 60 s | client address |
| `POST /api/auth/register` | 10 | 3600 s | client address |
| `POST /api/moderation/reports` | 10 | 3600 s | client address |
| `POST /api/job-seekers/apply` | 30 | 3600 s | client address |

Headers on limited responses: `X-RateLimit-Limit`, `X-RateLimit-Remaining`, and `Retry-After` when refused with `429`:

```json
{ "error": "Too many requests. Please wait 42 seconds and try again.", "status": 429, "retryAfterSeconds": 42 }
```

Limits are configuration (`JSM_RATE_LIMIT_*`), and `JSM_RATE_LIMIT_ENABLED=false` disables the filter.

## 16. OpenAPI

Spring-generated documentation is served at `/api-docs` (JSON) and `/swagger-ui.html`. It is publicly reachable by default; see `SECURITY_AND_LIMITATIONS.md` for the recommendation.

## 17. What this document deliberately omits

- Real passwords, JWT secrets, database credentials and the administrator access code.
- Internal service classes and repository method names (they are not part of the HTTP contract).
- Endpoints that do not exist: for example there is no chat, no email delivery, no payment route and no AI endpoint.
