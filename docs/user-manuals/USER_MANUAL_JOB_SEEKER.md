# Job Seeker User Manual

**Job & Skills Matching (JSM)** — for applicants looking for work in Basud, Daet, Vinzons or Labo, Camarines Norte.
Open <https://enuvex.onrender.com> in any current browser. The same pages work on a phone; the sidebar becomes a bottom navigation bar.

---

## 1. Create an account

1. On the landing page choose **Job Seeker** (or open `register.html` → **Job Seeker Registration**).
2. Fill in the form: full name, email, password, municipality (Basud / Daet / Vinzons / Labo), barangay, contact number, education, course, work experience, skills, employment type preference, work schedule, availability and expected salary. A profile photo is optional (max 5 MB).
3. Submit. You are signed in immediately and taken to your dashboard.

Notes:

- Your email is your sign-in name and cannot be duplicated.
- A photo is optional; everything else can also be edited later in **Profile**.
- If you see "Too many requests", the network has hit the registration limit; wait and try again.

## 2. Sign in

`login.html` → enter email and password → **Login**. A wrong password shows a single generic message; the system never says whether the email exists.

Sessions last about one hour. After that every page sends you back to the login page — this is normal, sign in again.

## 3. Complete your profile

**Sidebar → Profile** (`/job-seeker/profile.html`). Sections: personal details, location, education, work experience, certifications, skills, job preferences, expected salary, photo.

The dashboard shows a **profile completeness** card that counts only what influences your match score:

| Field | Weight |
| --- | --- |
| Skills | 25 |
| Work experience | 20 |
| Preferred location (municipality + barangay) | 20 |
| Education | 15 |
| Employment type preference | 10 |
| Expected salary | 10 |

Each missing item is a link that takes you to the right section. A higher completeness means more accurate percentages — not a guarantee of more matches.

### Managing skills

In **Profile → Skills** (and on **Skills & Career**) type a skill and add it, or pick from the catalogue. Skills are stored as a list separated by commas. Write them the way employers write them ("Customer Service", "Cashiering", "Welding"), because matching compares the words after normalising case and spacing.

## 4. Search for jobs

**Sidebar → Search Jobs** (`/job-seeker/jobs.html`).

Available filters: keyword (job title, skill, company), municipality, barangay, job category, employment type, work schedule, minimum/maximum salary, maximum distance from a point, match band and minimum match score.

- Results show title, employer, municipality/barangay, employment type, salary (or "Negotiable"), posting date and your **match badge**.
- Sorting options include newest first and highest match.
- **Save job** keeps a post in **Saved Jobs** without applying.
- Empty result? A message explains that no approved post matches the filters, with a reset action.

## 5. The job map

**Sidebar → Job Map** (`/job-seeker/map.html`).

- The map covers Basud, Daet, Labo and Vinzons and draws municipality and barangay boundaries.
- Only posts whose employer pinned a confirmed workplace appear as markers; posts without a pin remain in the list view.
- The **Project coverage** panel lists the four municipalities with their PSGC codes, barangay counts and number of active jobs; selecting one zooms to it.
- Filters: municipality, barangay, employment type and distance.
- **Near me** asks the browser for your position once. It is used only to measure distance for that search — never stored, never shared with employers.
- Selecting a marker previews the job with a link to its details.
- If the provider cannot be reached, the map area explains that no job has a confirmed location for the current filters, and **Open list view** keeps the search usable.

## 6. Read a job and its match

Open any card → **Job Details** (`/job-seeker/job-details.html?id=…`).

- Header: title, employer name, municipality and barangay, posting date, and the **match badge** (e.g. `80% Strong Match`).
- **Your Match**: the percentage, the band and the sentence "Based on N matched and M missing required skills".
- **Why this job matches you**: one line per criterion — Skills, Location, Employment type, Experience, Salary — each with what you have and what the post asks.
- **Required skills** shows each skill; missing ones are highlighted so you know what to add.
- **Before you apply** lists practical reminders (skills to add, commute, salary expectation).
- Actions: **Apply Now**, **Save Job**, **Report this posting**.

Bands: `Excellent Match` ≥ 90, `Strong Match` 75–89, `Good Match` 60–74, `Partial Match` 40–59, `Low Match` below 40. If a criterion cannot be judged (for example the employer published no salary), the page says so and leaves it out of the calculation instead of penalising you.

## 7. Recommended jobs

**Dashboard → Recommended for you** lists active posts ordered by your match score, with the band and the skills you are missing. Use it as a shortlist: applying to posts where you already match the listed skills is usually more productive.

## 8. Skills gap analysis and career guidance

**Sidebar → Skills & Career** (`/job-seeker/skills.html`).

- **Skills gap analysis**: compares the skills the local jobs demand with the skills in your profile. For each skill it shows how many active posts ask for it, whether you have it, and a priority.
- **Simulation**: choose one or more skills you could learn; the page recalculates how your match scores would change for current posts. Nothing is saved — it is a what-if.
- **Local demand**: rankings of the skills most requested in the four municipalities.
- **Career opportunities**: groups of related jobs with a search link.
- **Learning resources**: administrator-verified courses and materials per skill; you can record a learning interest so the demand statistics know what seekers want to learn.

## 9. Apply for a job

From the job page press **Apply Now**, or use the apply action on a card. Requirements:

- You must be signed in as a job seeker.
- You can apply to a post **once**; the second attempt is refused and the existing application remains.
- Your match score at the moment of application is stored with the application.

You cannot apply to a post that is not approved or no longer active.

## 10. Track your applications

**Sidebar → Applications** (`/job-seeker/applications.html`).

Each row shows the job, employer, date applied, current status and match at application time. Statuses:

`Submitted → Reviewing → Shortlisted → Interview → Accepted` (or `Rejected`), plus `Withdrawn` if you withdraw.

Open an application → **Application details** (`/job-seeker/application.html?id=…`):

- **Timeline**: every event with date, actor and message (submitted, reviewed, shortlisted, interview scheduled/rescheduled/cancelled, status change, withdrawal).
- **Interview information**: date and time, type, location or meeting link, instructions and the employer's message — present when the employer scheduled one.
- **Withdraw application**: available while the application is still in progress. Withdrawing is final for that application; you can re-apply only if the employer reopens the post and the system allows a new application.

## 11. Notifications

The bell in the top bar shows the unread count; **Sidebar → Notifications** opens the full inbox. Typical messages: employer reviewed your application, status changed, interview scheduled or moved, verification or moderation notices, report outcome.

You can mark one or all as read, and delete a notification. Notifications are in-app only — there is no email or SMS.

## 12. Saved jobs and reports

- **Saved Jobs** (`/job-seeker/saved-jobs.html`): posts you bookmarked, with unsave and open actions.
- **My Reports** (`/job-seeker/reports.html`): reports you submitted and their status (`Open`, `Under review`, `Resolved`, `Dismissed`).
- **Report a posting** (button on the job page): choose a category — scam/fraud, misleading information, fake employer, duplicate listing, wrong location, inappropriate content, other — and describe the problem. Your identity is visible only to administrators, never to the employer.

## 13. Common problems

| What you see | What to do |
| --- | --- |
| Sent back to the login page | The hour-long session expired; sign in again |
| "Too many requests" | Rate limit on that network; wait the number of seconds shown |
| A job is not in search results | It is not approved yet, was closed, or your filters exclude it |
| No markers on the map | No post with a confirmed workplace matches the filters; use the list view |
| Match percentage looks low | Check the explanation; completing the profile (skills, location, salary) raises the applicable weight |
| Cannot apply | Already applied, or the post is closed/not approved |
| Skills do not match | Write the skill as the employer wrote it; the engine compares exact words after normalisation |
