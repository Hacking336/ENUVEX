# Job & Skills Matching (JSM) — Project Overview

**Organization:** ENUVEX
**System:** Job & Skills Matching (JSM)
**Production URL:** <https://enuvex.onrender.com>
**Repository:** `Hacking336/ENUVEX` (branch `main`)
**Document status:** Final release (Phase 8)

---

## 1. Project name

Job & Skills Matching, abbreviated **JSM** (deployed under the ENUVEX product name).

## 2. Project background

Basud, Daet, Vinzons and Labo are neighbouring municipalities in Camarines Norte. Their economies are made up mostly of small and medium enterprises — retail shops, food service, offices, construction, transport, agriculture and fisheries, healthcare, education and light manufacturing — rather than large corporate employers with formal recruitment departments.

In that setting, hiring happens informally: tarpaulins, social-media posts, word of mouth, and walk-in inquiries. Job seekers therefore see only the vacancies they happen to hear about, and employers receive applications from people whose skills and location do not match the post. Neither side has a shared place to record skills, requirements, applications or hiring outcomes.

JSM was built as a single web application that gives both sides that shared place, restricted to the four municipalities above, and adds a transparent match score so a seeker can see *why* a vacancy suits them.

## 3. Problem statement

Local job seekers and local employers have no common, structured channel:

1. **Fragmented vacancies.** Postings are scattered across informal channels, so seekers miss posts and employers repeatedly re-advertise.
2. **Skills mismatch.** Vacancies list skills, education, experience and salary expectations, but applicants cannot compare those against their own profile before applying, so both sides spend time on unsuitable applications.
3. **Distance and location ambiguity.** A vacancy's barangay matters in a rural municipality; informal posts rarely state it, and seekers cannot tell whether a job is reachable.
4. **No tracking.** Applications are made verbally or by message, with no recorded status, timeline or interview details.
5. **No accountability.** Seemingly fake, misleading or duplicate listings cannot be reported, reviewed or removed, and employers cannot be verified.
6. **No visibility for employers.** Employers cannot see how their posts perform, where applicants come from, or which stages lose candidates.

## 4. General objective

To design and develop a web-based job and skills matching system for the municipalities of Basud, Daet, Vinzons and Labo, Camarines Norte, that connects local job seekers with local employers through structured profiles, geographic job search and a transparent skills-based match score, with moderation to keep listings credible.

## 5. Specific objectives

| # | Objective | Delivered as |
| --- | --- | --- |
| 1 | Provide role-based accounts for job seekers, employers and administrators | Registration, login and three separate workspaces |
| 2 | Capture structured seeker profiles (skills, education, experience, preferences) | Profile pages with a matching-relevant completeness indicator |
| 3 | Capture structured employer and vacancy data | Company profile, verification request, job posting form |
| 4 | Support geographic search inside the coverage area | Municipalities, barangays, map view, radius search, PSGC codes and boundaries |
| 5 | Score and explain job–seeker fit deterministically | Five weighted criteria, per-criterion explanation, band classification |
| 6 | Track applications through a status lifecycle with a timeline | Status history, interviews, notifications |
| 7 | Guide seekers on missing skills and local demand | Skills gap analysis, simulation, career opportunities, learning resources |
| 8 | Give employers hiring analytics | Overview, funnel, response metrics, matching, job performance, geographic insights, CSV export |
| 9 | Keep listings credible | Employer verification, job approval workflow, reports, rule-based suspicious listing detection, audit trail |
| 10 | Secure and verify the release | JWT sessions, role separation, hardened headers, persistent rate limiting, 58 automated tests |

## 6. Target users

| Role | Who they are | What they need |
| --- | --- | --- |
| **Job seeker** | Residents of the four municipalities looking for local work, including students, fresh graduates and career shifters | Find nearby vacancies, understand their fit, apply, and follow up |
| **Employer** | Owners, managers or staff of local businesses who hire | Post vacancies, review ranked applicants, schedule interviews, see hiring data |
| **Administrator** | Platform operator (ENUVEX) | Verify employers, approve listings, handle reports, moderate accounts, review actions |

## 7. Scope

### 7.1 Geographic scope

The system is limited to four municipalities of Camarines Norte:

| Municipality | PSGC | Barangays in the reference layer |
| --- | --- | --- |
| Basud | 0501601000 | 29 |
| Daet | 0501603000 | 25 |
| Labo | 0501606000 | 52 |
| Vinzons | 0501612000 | 19 |

Barangay names, codes and boundary polygons come from PSA PSGC-aligned reference GeoJSON bundled with the application (`geo/municipalities.geojson`, `geo/barangays.geojson`, 125 barangays in total). They are a **reference layer**: being listed means a barangay exists in the coverage area, not that it contains a vacancy. A barangay appears with jobs only when an employer has pinned a workplace there, and the map plots only posts whose employer confirmed a workplace location.

**The deployment does not contain every employer, every vacancy or every barangay of these municipalities.** Records in the system are those created through registration and posting; the four-municipality limit is enforced by an enumeration (`Municipality`), so a post outside the coverage area cannot be created.

### 7.2 Functional scope

- Accounts and profiles for three roles.
- Job posting with moderation, search, filtering, mapping and matching.
- Applications with status lifecycle, interviews and notifications.
- Skills gap analysis, career guidance and learning resources.
- Employer analytics with CSV export.
- Administrator moderation, reporting and audit logging.
- Persistent rate limiting on sign-in, registration, report and application submission.

### 7.3 Out of scope (deliberately)

- Municipalities outside the four above; other regions or countries.
- Payroll, attendance, timekeeping, contracts or e-signatures.
- Chat or direct messaging between users (notifications are one-way system messages).
- Online payments or paid job-posting tiers.
- AI or machine-learning matching, resume parsing, automatic ranking by a model.
- Native mobile applications (the UI is responsive and used on phones, but it is a web app).
- A public recruitment-agency marketplace or cross-posting to other job boards.

### 7.4 Limitations

1. **Match score is rule-based.** It is deterministic and explainable, but it cannot judge soft skills, personality, portfolio quality or interview performance, and it relies on what the seeker and employer wrote.
2. **Data quality depends on users.** A vacancy with no listed skills, salary or experience requirement simply drops those criteria from the score (see `MATCHING_ENGINE.md`); a seeker who has not filled in a field loses the chance to earn those points.
3. **Map coverage depends on pins.** A post without a confirmed workplace location is still listed and searchable, but it is not plotted.
4. **Single-region deployment.** Production runs on a free hosting tier that sleeps when idle; the first request after a sleep is slower.
5. **No messaging.** Employers and seekers coordinate interviews through the structured interview record and notifications, not a chat.
6. **No email/SMS delivery.** Notifications are in-app only.
7. **Administrator sign-in shortcut** requires a deployment secret (`JSM_ADMIN_ACCESS_CODE`); the seeded administrator password is a placeholder that must be rotated by the operator.
8. **Known security limitations** are documented in `SECURITY_AND_LIMITATIONS.md` (JWT in `localStorage`, no CSP, no token refresh, browser-side pagination on some admin lists, public API docs).

## 8. Project coverage by phase

| Phase | Title | What it added | Status |
| --- | --- | --- | --- |
| 1 | Geographic mapping | Municipality/barangay reference data, boundaries, job pins, radius and near-me search, map view | Complete |
| 2 | Matching & recommendation engine | Deterministic five-criterion score with explanations, recommendations, completeness indicator | Complete |
| 3 | Application tracking & notifications | Status lifecycle, timeline, interviews, notification inbox, saved jobs | Complete |
| 4 | Skills gap analysis & career guidance | Skill demand, gap analysis, simulation, career groups, learning resources | Complete |
| 5 | Employer analytics & hiring insights | Overview, funnel, response metrics, matching, job performance, geographic insights, insights, CSV export | Complete |
| 6 | Admin moderation & platform management | Employer verification, job approval, reports, suspicious listing detection, account suspension, audit log | Complete |
| 7 | Security audit, QA & performance | Admin sign-in hardening, session revocation, page-size caps, headers, health readiness, error mapping, analytics page repair | Complete |
| 8 | Final release & documentation | This documentation set, presentation, persistent rate limiting, final verification | Complete |

## 9. Technology stack (as implemented)

| Layer | Technology | Notes |
| --- | --- | --- |
| Language | Java 21 | `java.version` 21 in `backend/pom.xml` |
| Framework | Spring Boot 3.4.2 | `spring-boot-starter-web`, `-data-jpa`, `-security`, `-validation` |
| Security | Spring Security + JJWT 0.12.5 | Stateless JWT, BCrypt password hashing |
| Persistence | Spring Data JPA / Hibernate, PostgreSQL | `ddl-auto: validate`; all tables live in schema `enuv` |
| Migrations | Flyway (core + postgresql) | V1–V9, `clean-disabled: true` |
| API docs | springdoc-openapi 2.8.0 | `/api-docs`, `/swagger-ui.html` |
| Frontend | Static HTML5, CSS3, vanilla JavaScript | Served by the same Spring Boot process |
| Map | Leaflet 1.9.4 + Leaflet.markercluster 1.5.3 (via unpkg) | Tiles from an OpenStreetMap-compatible provider, configured server-side |
| Icons / fonts | Font Awesome 6.4 (CDN), Poppins + Inter (Google Fonts) | Brand palette: Navy `#102B4C`, Green `#2FA247`, Dark Green `#25853A`, Soft Green `#EAF5EC`, background `#F5F8FA` |
| Build | Maven 3.9 wrapper-free, multi-stage Dockerfile | Jar plus frontend assets |
| Hosting | Render (Docker web service) | Auto-deploy from `main` |
| Database hosting | Supabase PostgreSQL | Production database |
| Tests | JUnit 5, Spring Boot Test, AssertJ | 58 tests |

**Not used:** no Node/npm build step, no React/Vue/Angular, no Redis, no message queue, no AI service, no paid third-party API.

## 10. Main system capabilities

**Job seeker**

- Register (with optional photo), sign in, edit profile and skills.
- Search and filter by municipality, barangay, category, skills, salary, employment type, work schedule, distance and match band.
- Use the map view, including "Near me" radius search (position used for that search only).
- See a match percentage, band and per-criterion explanation for every job.
- Browse recommended jobs sorted by score, save jobs for later.
- Run skills gap analysis, simulate added skills, follow learning resources and career opportunities.
- Apply, track status and timeline, view interview details, withdraw.
- Receive in-app notifications; report a suspicious listing.

**Employer**

- Register, maintain company profile, request verification.
- Post, edit, close and delete vacancies; resubmit after requested changes.
- See moderation status on every post.
- Review applicants ranked by match score, open an applicant profile, change status, schedule/reschedule/cancel interviews.
- View analytics: overview, applicant funnel, response metrics, matching insights, job performance, geographic insights, recruitment insights, skills analysis per post, CSV export, date-range filtering.

**Administrator**

- Moderation dashboard with pending counts.
- Verify or reject employers, or request more information.
- Approve, reject, request changes on, restore or delete job posts.
- Review reports, mark under review, resolve or dismiss; see report statistics.
- Run suspicious-listing detection, review flags, dismiss with a reason.
- List users, suspend and restore accounts.
- Review the append-only audit trail.
- Manage learning resources and view skills analytics.

## 11. Completion status

All eight planned phases are implemented and deployed. The final deployed commit for this release is recorded in `RELEASE_CHECKLIST.md`; test evidence is in `TESTING_AND_QA_REPORT.md`, and open limitations in `SECURITY_AND_LIMITATIONS.md`.
