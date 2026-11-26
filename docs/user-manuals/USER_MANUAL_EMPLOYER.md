# Employer User Manual

**Job & Skills Matching (JSM)** — for businesses in Basud, Daet, Vinzons and Labo, Camarines Norte.
Open <https://enuvex.onrender.com> and choose **Employer**, or go to `employer/register.html`.

---

## 1. Register your company

1. **Employer Registration** asks for: email, password, business name, employer (contact) name, business type (retail, food service, hospitality, office, healthcare, education, construction, agriculture, fisheries, manufacturing, logistics, transport, automotive, security, IT, other), municipality, barangay, business address, contact number, business description and an optional logo (max 5 MB).
2. Submit — you are signed in and land on the employer dashboard.
3. Your company starts as **Unverified** (`UNVERIFIED`). You can post jobs and receive applications immediately; verification adds a "Verified" mark that seekers see.

## 2. Sign in

`login.html` → email and password → **Login**. Sessions last about one hour; you will be asked to sign in again after that. Repeated wrong attempts from the same network are throttled (default 20 per minute).

## 3. Company profile and verification

**Sidebar → Company Profile** (`/employer/profile.html`): business details, address, description, logo, contact information, and the current verification state.

Verification states: `UNVERIFIED`, `PENDING`, `VERIFIED`, `REJECTED`, `NEEDS_INFORMATION`.

- Press **Request verification** to move to `PENDING`. The administrator sees it in the verification queue.
- If the administrator rejects the request or asks for information, the reason appears on the profile and in your notifications. Update the details and request again.
- Verification is granted by the administrator only; there is no way to verify yourself from the employer side.

## 4. Post a job

**Sidebar → Post a Job** (`/employer/post-job.html`). Fields:

| Group | Fields |
| --- | --- |
| Role | Job title, category (20 options), employment type (part-time, full-time, contract, temporary), available positions |
| Description | Job description, responsibilities, required skills (comma separated), certifications |
| Requirements | Education requirement, experience requirement (e.g. "No experience required", "1-2 years") |
| Pay and schedule | Minimum and maximum salary (leave blank for "Negotiable"), work schedule |
| Location | Municipality (required, one of the four), barangay, street/landmark, and a **map pin** |
| Visibility | Whether the post is active |

**The map pin matters.** A post is searchable and listable without a pin, but it appears on the Job Map only when the workplace location is confirmed (latitude, longitude, PSGC municipality and barangay codes). Use the picker on the posting form; the coordinates and codes are stored together or not at all.

On submit the post is created with moderation status **`PENDING_REVIEW`** and is not visible to seekers until an administrator approves it.

## 5. Moderation status of a post

**Sidebar → My Job Posts** (`/employer/jobs.html`) shows every post with its moderation badge:

| Status | Meaning | What you can do |
| --- | --- | --- |
| `PENDING_REVIEW` | Waiting for the administrator | Wait; editing is allowed but keeps it in review |
| `APPROVED` | Visible in search, recommendations and the map | Edit if needed (an edit returns it to review), close, or delete |
| `CHANGES_REQUESTED` | The administrator wants corrections | Read the reason in the notification, edit the post, save — it goes back to review |
| `REJECTED` | Not published | Read the reason; correct the post and save an edit to resubmit |

You also receive a notification for every decision (`JOB_POST_APPROVED`, `JOB_POST_CHANGES_REQUESTED`, `JOB_POST_REJECTED`).

**Editing and resubmitting**: open the post, change the fields, save. Any edit to an approved post sends it back to `PENDING_REVIEW` so the published version is always one an administrator reviewed. Closing a post (`is_active = false`) hides it from seekers without deleting the applications already received.

## 6. Review applicants

**Sidebar → Applicants** (`/employer/applicants.html`).

- Choose one of your posts; applicants are listed **ranked by match score**, highest first.
- Each row: name, municipality/barangay, education, skills, the match badge and the application status.
- Open an applicant (`/employer/applicant-profile.html`) to see the profile, skills, experience and the criterion-by-criterion explanation of why the score is what it is.
- You only ever see applicants to **your own** posts; the API refuses any other post, even if you guess its id.

The score is a compatibility indicator, not a hiring decision — the system states this next to the ranking.

## 7. Update application statuses

From the applicant list or the applicant page, choose a new status:

`SUBMITTED → REVIEWING → SHORTLISTED → INTERVIEW → ACCEPTED` or `REJECTED` at any point.

- Each change records the previous status, the new one, who did it and an optional message, and appears in the applicant's timeline.
- The applicant is notified automatically.
- If the applicant withdraws, the status becomes `WITHDRAWN` and you are notified; you cannot change a withdrawn application.

## 8. Schedule interviews

On an application choose **Schedule interview** and fill in: date and time, type (e.g. in-person, phone, online), location or meeting link, instructions and a message for the applicant.

- The applicant receives `INTERVIEW_INVITE` with the details, and the application moves to `INTERVIEW`.
- **Reschedule** sends `INTERVIEW_RESCHEDULED`; **Cancel** sends `INTERVIEW_CANCELLED` and removes the interview record. One interview per application at a time.

## 9. Notifications

The bell shows unread counts; **Sidebar → Notifications** (`/employer/notifications.html`) lists messages: new application received, applicant withdrew, administrator decisions on posts and verification, report outcomes about your postings.

In-app only — JSM does not send email or SMS.

## 10. Analytics dashboard

**Sidebar → Employer Analytics** (`/employer/analytics.html`). Everything here is computed from your own posts and applicants.

| Section | What it shows |
| --- | --- |
| Overview cards | Total job posts, active posts, total applicants, new applicants, awaiting review, interviews scheduled, accepted, rejected |
| Job Posting Performance | One row per post: title, municipality, employment type, date posted, status, applicants, new applicants, shortlisted, interviews; sortable and filterable, paged |
| Applicant funnel | Applied → reviewed → shortlisted → interview → accepted, with the count and share at each stage |
| Response metrics | How quickly you respond, response rate, applications still unanswered |
| Matching insights | Distribution of match scores, average and band counts for your applicants |
| Skills analysis per post | Which required skills applicants have and which are usually missing |
| Geographic insights | Where your applicants come from (municipality/barangay) |
| Recruitment insights | Plain-language observations derived from the numbers above |

**Date filtering**: choose `7d`, `30d`, `90d` or `all`. The range applies to every section and to the export.

## 11. CSV export

Press **Export CSV** on the analytics page. The page fetches `/api/employers/analytics/export` with your token and builds the CSV in the browser, so the file matches what you see.

The file is organised in labelled sections (overview, funnel, response metrics, matching, per-post performance, geographic). Cells are quoted, and any value starting with `=`, `+`, `-`, `@`, tab or carriage return is prefixed so a spreadsheet cannot execute it as a formula.

## 12. Limitations you should know

1. **Approval is required.** A new or edited post is not public until an administrator approves it.
2. **The map needs a pin.** Posts without a confirmed workplace are invisible on the map (still listed).
3. **Matching is rule-based.** It compares skills, location, employment type, experience and salary, exactly as entered. It does not read CVs, does not consider soft skills and does not learn from your previous hires.
4. **No chat.** Communication is through status changes, the interview record and notifications; use the contact details on the applicant profile.
5. **No email sending.** Notifications stay in the app.
6. **One application per seeker per post.** Deleting an application is not possible; a seeker withdraws instead.
7. **Analytics use your data only.** There is no cross-employer benchmarking.
8. **Public job pages are visible without an account.** Applicants' details are not.

## 13. Common problems

| What you see | What to do |
| --- | --- |
| Post not visible to seekers | Check the moderation badge; `PENDING_REVIEW`, `CHANGES_REQUESTED` and `REJECTED` are not public |
| "Changes requested" but the post looks fine | Open the notification for the administrator's reason |
| No applicants | Check that the post is approved and active; consider listing required skills and a salary range |
| Applicants appear without a score | Their profile is incomplete; the score is computed from what they entered |
| Export downloads nothing | You may have no posts in the selected date range; choose `all` |
| Map pin missing on a post | Edit the post and use the location picker |
| "Too many requests" on login | Rate limit on the network; wait the seconds shown |
