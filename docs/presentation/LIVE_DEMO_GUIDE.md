# Live Demonstration Guide

A 12–15 minute demonstration of the real application, in the order that tells one story: a seeker finds a fitting job and applies, the employer reviews the ranked applicants, the administrator keeps the listing credible.

## Before you start

| Item | Detail |
| --- | --- |
| Where to run it | **Preferred: a local instance** with an isolated database (`INSTALLATION_AND_DEPLOYMENT.md` §5). Backup: the production site, read-only where possible |
| Never do | Do not create fake accounts, jobs, reports or applications in the **production** database during the demo |
| Warm up the host | Free hosting sleeps. Open the site 2–3 minutes early so the first request is not a 6-second wait |
| Sessions | Tokens last about one hour; sign in fresh before the demo begins |
| Accounts | Use the demo accounts prepared in the isolated database, or the operator's own. Never read out a password on screen |
| Browser prep | Two tabs: one signed in as seeker, one as employer. Sign in as administrator only when you reach step 12 |
| Windows to close | Any terminal showing environment variables, any database console |

**Accounts needed:** one job seeker with a completed profile (skills, municipality, barangay, preference, expected salary), one employer with an approved post, one administrator account.

## Step 1 — Job seeker signs in

- **URL:** `/login.html`
- **Role:** job seeker
- **Click:** email + password → **Login**
- **Observe:** landing on `/job-seeker/dashboard.html`; greeting, counters, profile completeness card, recommended jobs
- **Expected:** completeness shows which matching fields are missing; recommended jobs carry a band badge
- **Fallback:** if the session is stale you land back on login — sign in again. If the dashboard is empty, the account has no profile data: switch to the prepared account

## Step 2 — Browse jobs

- **URL:** `/job-seeker/jobs.html`
- **Click:** type a keyword (for example a skill) → **Search**; then set municipality and employment type
- **Observe:** result cards with title, employer, municipality/barangay, salary or "Negotiable", date, match badge
- **Expected:** only approved, active posts; filters narrow the list; an empty result shows an explanatory message
- **Fallback:** if nothing matches, clear the filters and use the municipality dropdown alone

## Step 3 — Geographic map

- **URL:** `/job-seeker/map.html`
- **Click:** select **Daet** in the coverage panel; then a job marker if one is plotted
- **Observe:** boundaries drawn, coverage panel with PSGC codes and barangay counts, marker preview
- **Expected:** only posts with a confirmed workplace appear; if none match, the map says so and offers **Open list view**
- **Fallback:** say out loud that this is a data question, not a bug — posts without a pin are intentionally not plotted — and open the list view

## Step 4 — Match explanation

- **URL:** `/job-seeker/job-details.html?id=<job id>`
- **Click:** open a card from step 2, scroll to **Your Match**
- **Observe:** percentage and band, "Based on N matched and M missing required skills", one line per criterion (Skills, Location, Employment type, Experience, Salary), highlighted missing skills
- **Expected:** every criterion has a sentence explaining its contribution; unavailable criteria say why they were left out
- **Fallback:** if the chosen post has no required skills, open a post that lists several — that one demonstrates the skills criterion properly

## Step 5 — Skills gap analysis

- **URL:** `/job-seeker/skills.html`
- **Click:** scroll through *demand*, *your gap*, run a **simulation** with one skill added
- **Observe:** per-skill demand counts, the gap highlighted, the simulated change in match scores, learning resources
- **Expected:** numbers change after the simulation; nothing is saved
- **Fallback:** if the dataset has few posts, describe the calculation and show the resource list

## Step 6 — Apply

- **URL:** `/job-seeker/job-details.html?id=<job id>`
- **Click:** **Apply Now**, then confirm
- **Observe:** success message; the button state changes; a notification appears in the bell
- **Expected:** one application per post; pressing again is refused with "already applied"
- **Fallback:** if already applied, show the refusal — it demonstrates the duplicate rule — and move on

## Step 7 — Track the application

- **URL:** `/job-seeker/applications.html` → open the application (`application.html?id=…`)
- **Click:** open the row
- **Observe:** status, match at application time, timeline of events, interview panel if one exists
- **Expected:** timeline starts with "submitted"; no interview yet
- **Fallback:** use an older application that already has a review or interview event, so the timeline is richer

## Step 8 — Employer signs in

- **URL:** second tab, `/login.html`
- **Role:** employer
- **Click:** sign in
- **Observe:** `/employer/dashboard.html` — posts, active posts, applicants, new applicants, interviews
- **Expected:** counters match the employer's own data only
- **Fallback:** none needed; if counters are zero the account has no approved post — switch to the prepared employer

## Step 9 — Post a job and its moderation status

- **URL:** `/employer/post-job.html`
- **Click:** fill the minimum required fields, pick a municipality and barangay, optionally pin the map, submit
- **Observe:** the new post on `/employer/jobs.html` with the **Pending review** badge
- **Expected:** the post is not yet visible to seekers (verify in the seeker tab's search, or state it)
- **Fallback:** if you do not want to create data, open an existing post and point at its badge instead

## Step 10 — Applicants and match insights

- **URL:** `/employer/applicants.html`
- **Click:** choose the post, open an applicant
- **Observe:** applicants ranked by match score, each with band and status; applicant profile with the criterion breakdown
- **Expected:** the same percentage the seeker saw, computed by the same engine
- **Fallback:** with one applicant, show that applicant and describe the ranking rule

## Step 11 — Employer analytics

- **URL:** `/employer/analytics.html`
- **Click:** change the date range to **All**; scroll to the funnel; press **Export CSV**
- **Observe:** overview cards, funnel, response metrics, matching distribution, per-post table, geographic insights; the downloaded CSV opens in a spreadsheet
- **Expected:** every number derived from this employer's posts; CSV sections match the screen
- **Fallback:** if the download is blocked by the browser, say the file is generated client-side from the same JSON the page uses, and show the network response instead

## Step 12 — Administrator signs in

- **URL:** new tab, `/login.html`
- **Role:** administrator (email + password, or `admin++` with the deployment access code typed in the **password** field)
- **Click:** sign in
- **Observe:** `/admin/dashboard.html` with platform counters and pending queues
- **Expected:** admin navigation appears; every page works
- **Fallback:** if the shortcut is refused, the access code is not configured on that deployment — use the administrator email and password. Never read the code aloud, never type it into the email field

## Step 13 — Verify an employer

- **URL:** `/admin/employers.html`
- **Click:** open a pending employer → **Verify** (or **Request information** with a message)
- **Observe:** status changes, confirmation message
- **Expected:** the employer receives a notification; an audit entry is created
- **Fallback:** if nothing is pending, open a verified employer and show the decision history and its audit rows instead

## Step 14 — Approve a job posting

- **URL:** `/admin/job-posts.html`
- **Click:** filter **Pending review**, open the post created in step 9, press **Approve**
- **Observe:** status becomes **Approved**
- **Expected:** the post now appears in the seeker's search and, if pinned, on the map; the employer is notified
- **Fallback:** if no post is pending, open an approved post and show its moderation history and audit entry

## Step 15 — Report and suspicious listing review

- **URL:** `/admin/moderation-reports.html`, then `/admin/suspicious-listings.html`
- **Click:** open an open report, mark **Under review**, then **Resolve** with a note; on the suspicious page press **Run detection**
- **Observe:** the report leaves the open queue; detection returns flags per rule with human-readable reasons
- **Expected:** the reporter is notified of the resolution; re-running detection does not duplicate an open flag
- **Fallback:** if there are no open reports, show a resolved one with its notes and the corresponding audit entry

## Step 16 — Audit logs

- **URL:** `/admin/audit-logs.html`
- **Click:** filter by action type (for example the verification or approval performed minutes earlier)
- **Observe:** entries with administrator, target, reason, previous state and new state
- **Expected:** the demo's decisions are at the top; there is no edit or delete control anywhere
- **Fallback:** search by target type and read the newest three entries aloud

## Closing line

> Every action you just saw — verification, approval, report resolution — is in the audit trail, and every match percentage came from the same engine that scored the applicant. That is the whole design: one source of truth for data, one for scoring, and one for accountability.

## Failure playbook

| Symptom | Cause | What to do |
| --- | --- | --- |
| Page loads but every list is empty, redirect to login | Expired token (about one hour) | Sign in again; say the session lifetime out loud |
| First request takes 5–8 s | Free hosting tier waking up | "The free tier sleeps when idle; the first request wakes it" |
| Map is grey | Tile provider or CDN unreachable | Fall back to list view; the rest of the app is unaffected |
| 429 on sign-in | Rate limit for that network (default 20 per minute) | Wait for the retry time; explain the limiter |
| Cannot approve: post already approved | Someone edited it back to review, or another admin approved | Refresh the list and pick another pending post |
| CSV download blocked | Browser policy on the kiosk | Show the network response and describe the generated file |
| A demo account is missing | Isolated database rebuilt | Use the other prepared account of the same role; do not create production data |
| Admin login refused | Access code not set for that environment | Use the administrator email and password |

## Practice checklist

1. Run the whole sequence once on the isolated database and note the ids of the job, application and employer you will show.
2. Time it: 12–15 minutes is the target.
3. Prepare two applicants if you want to demonstrate the ranking.
4. Confirm the CSV opens on the presentation machine.
5. Have this guide open on a second screen; do not read it aloud verbatim.
