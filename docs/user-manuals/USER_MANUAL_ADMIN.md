# Administrator User Manual

**Job & Skills Matching (JSM)** — platform operator guide (ENUVEX).
Administrative pages live under `/admin/` on the same host as the public site. This manual never prints the access code; read it from the deployment environment (see §1).

---

## 1. Administrator authentication

There are two ways to sign in at `login.html`:

1. **Email and password** of an account whose `user_type` is `ADMIN`. This is the normal method and the one to use in production after rotating the seeded password.
2. **Administrator shortcut**: email `admin` or `admin++` with the deployment's `JSM_ADMIN_ACCESS_CODE` entered in the **password** field.

Rules that matter:

- The shortcut is **disabled** when `JSM_ADMIN_ACCESS_CODE` is not set. In that case use method 1.
- A wrong code and a missing code produce the same generic error; the system never confirms a guessed value.
- The code is a deployment secret. Keep it in Render → Environment (or your secret manager). Do not commit it, paste it into documentation, or email it.
- The seeded administrator password in migration V1 is a public placeholder and **must be rotated** by the operator.

Sessions last about one hour. Suspending the account you are signed in as takes effect on the next request.

## 2. Dashboard

**`/admin/dashboard.html`** — platform counters and the moderation queue summary: total users and accounts by role, job posts by moderation status, applications, open reports, open suspicious-listing flags, pending employer verification. Each counter links to the page that resolves it.

The top bar has the notification bell and the account menu; the sidebar reaches every administrative page.

## 3. Employer verification

**`/admin/employers.html`** — list of employer accounts with business name, municipality, contact, verification status and date.

- **Requested (PENDING)** employers appear at the top; open one to see the profile, address, description and contact details.
- **Verify** — approves the employer: status `VERIFIED`, the employer is notified (`EMPLOYER_VERIFIED`), and an audit entry `EMPLOYER_VERIFIED` is written.
- **Reject verification** — requires a reason: status `REJECTED`, the reason is sent to the employer (`EMPLOYER_REJECTED`).
- **Request information** — requires a message: status `NEEDS_INFORMATION`, the employer receives `VERIFICATION_INFO_REQUESTED`.

Verification is a judgement about the business, not a background check. JSM does not validate business registrations, permits or identities automatically.

## 4. Job posting approval

**`/admin/job-posts.html`** — every post with employer, title, municipality, employment type, status and date. **Pending review** filters the queue.

Reviewing a post: read the description, responsibilities, required skills, salary and location pinning.

- **Approve** → `APPROVED`: the post becomes visible in search, recommendations and the map. The employer is notified (`JOB_POST_APPROVED`).
- **Reject** (reason required) → `REJECTED`: not published; the employer is told why.
- **Request changes** (reason required) → `CHANGES_REQUESTED`: the employer edits and resubmits; the post returns to `PENDING_REVIEW`.
- **Restore** → returns a closed or rejected post to an active state.
- **Delete** → removes the post permanently (its applications go with it). Prefer rejecting or closing unless content must be erased.

Every decision writes an audit row with the previous and new state and a notification to the employer. **Any employer edit to an approved post sends it back to review** — that is intentional, so the public version is always one you approved.

## 5. Reported listings

**`/admin/moderation-reports.html`** — reports submitted by users, filterable by status (`OPEN`, `UNDER_REVIEW`, `RESOLVED`, `DISMISSED`).

Each report shows the target (job posting, employer or user), category (fraud/scam, misleading information, fake employer, duplicate listing, wrong location, inappropriate content, other), the reporter's description and date. Reporter identity is visible **only** to administrators.

Actions:

- **Mark under review** → `UNDER_REVIEW`, so another administrator knows it is being handled.
- **Resolve** (notes) → `RESOLVED`; the reporter is notified (`REPORT_RESOLVED`).
- **Dismiss** (notes) → `DISMISSED`, for invalid, duplicate or unfounded reports.

Resolving a report about a post is not automatic moderation: approve, reject or request changes on the post itself, then resolve the report with a note saying what was done.

## 6. Suspicious listing review

**`/admin/suspicious-listings.html`** — flags produced by the rule-based detector.

**Run detection** (`POST /api/admin/suspicious-listings/detect`) re-scans current posts. Six rules exist:

| Rule | What it notices |
| --- | --- |
| `DUPLICATE_TITLE` | The same title posted repeatedly by one employer |
| `DUPLICATE_DESCRIPTION` | Near-identical description text |
| `SAME_TITLE_AND_LOCATION` | The same title at the same municipality/barangay |
| `MISSING_REQUIRED_INFORMATION` | Post lacks the information a credible listing needs |
| `REPEATED_REJECTED_LISTING` | An employer repeatedly reposting rejected content |
| `SUSPICIOUS_PAYMENT_REQUEST` | Wording asking applicants for money, fees or deposits |

Each flag records a human-readable reason and the rule that fired. One open flag per rule per post is possible; re-running detection cannot duplicate an open flag, but a corrected repost can be flagged again.

**Dismiss** a flag (reason required) when the post is legitimate. Dismissed flags are kept for the record. Flagged posts are not removed automatically — moderation of the post remains your decision.

## 7. Account moderation

**`/admin/users.html`** — all accounts with email, role, verification state, active state and creation date.

- **Suspend** (reason required): sets `is_active = false`. The account can no longer sign in **and any token already issued stops working on the next request**. Existing applications, posts and history are preserved.
- **Restore**: sets `is_active = true` and notifies the user (`ACCOUNT_RESTORED`).
- You cannot suspend your own account, and the system refuses to suspend the last remaining administrator.

Suspension is reversible and non-destructive; there is no hard-delete of accounts in the interface.

## 8. Audit logs

**`/admin/audit-logs.html`** — the append-only trail, newest first, filterable by action and target type.

Each entry: action (`EMPLOYER_VERIFIED`, `EMPLOYER_REJECTED`, `VERIFICATION_INFO_REQUESTED`, `JOB_POST_APPROVED`, `JOB_POST_REJECTED`, `JOB_POST_CHANGES_REQUESTED`, `JOB_POST_RESTORED`, `JOB_POST_DELETED`, `REPORT_RESOLVED`, `REPORT_DISMISSED`, `REPORT_UNDER_REVIEW`, `SUSPICIOUS_LISTING_FLAGGED`, `SUSPICIOUS_LISTING_DISMISSED`, `USER_SUSPENDED`, `USER_RESTORED`, learning-resource changes), the administrator, target type and id, reason, previous state, new state and time.

There is **no route to edit or delete an entry**. If an action is wrong, correct it with a new action; the history shows both.

## 9. Skills analytics and learning resources

**`/admin/skills.html`** — demand (what active posts ask for) versus recorded seeker supply and learning interest, with gaps highlighted. Use it to curate resources.

Learning resources (title, provider, URL, skill, cost, free/online, duration, description, availability, active flag) are managed from the same area (`GET/POST/PUT/DELETE /api/admin/learning-resources`). Only administrators can create or edit them; seekers see them on **Skills & Career**.

## 10. Reports and platform analytics

- **`/admin/reports.html`** — platform-wide summary: applications by status, users, employers, posts, reports.
- **`/admin/geography.html`** — same map engine as the public map, with coverage and job counts per municipality/barangay and a per-area breakdown (`/api/admin/geo/stats`).
- **`/admin/skills.html`** — demand/supply statistics (`/api/admin/skills-analytics`, `/api/admin/skills-demand`).
- Matching statistics are available at `/api/admin/matching/stats`.

## 11. Notifications

The bell and `/admin/notifications.html` show system notifications addressed to administrator accounts (for example a new suspicious listing flag).

## 12. Role restrictions

- Every `/admin/` API route requires `hasRole('ADMIN')` at both URL and method level; a seeker or employer token gets `403`, an anonymous request `401`.
- The administrative pages are static files and can be *loaded* by anyone, but nothing on them works without an administrator token.
- There is no "super administrator" and no impersonation feature: to reproduce a user's problem, ask them for details or inspect the audit trail.
- Anti-lockout rules: cannot suspend yourself, cannot suspend the last administrator.

## 13. Routine operating checklist

| Cadence | Task |
| --- | --- |
| On every deploy | Open `/api/health` (expect `{"status":"UP","database":"UP"}`) and one page from each role |
| Daily | Clear the pending verification and job review queues; handle open reports and flags |
| Weekly | Read the audit trail for unexpected actions; review suspended accounts |
| Monthly | Rotate the administrator access code; confirm the database backup policy; check dependency updates |
| On suspected abuse | Suspend the account, record the reason, review the audit log; the rate limiter already slows automated attempts |

## 14. Common problems

| What you see | What to do |
| --- | --- |
| `admin++` refused | The code is unset on the deployment, or it was typed into the email field. Use the admin email + password, or set/read `JSM_ADMIN_ACCESS_CODE` in Render → Environment |
| Approve button does nothing | The session expired (about one hour); sign in again |
| Post shows `PENDING_REVIEW` right after you approved it | The employer edited it; editing returns a post to review |
| Cannot suspend an account | It is your own account or the last administrator |
| Report disappeared from the queue | Another administrator resolved or dismissed it; check the audit log |
| Map page empty | The administrator view needs posts with confirmed pins; check the missing-pin indicator on the post |
| Export/reports numbers seem stale | Analytics are computed on request from current data; refresh the page |
