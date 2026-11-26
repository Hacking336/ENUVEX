# Job & Skills Matching System

A localized job marketplace and matching platform for job seekers and employers within the municipalities of Basud, Daet, Vinzons, and Labo in Camarines Norte.

## System Overview

This web-based system connects local job seekers with local employers across various industries, not limited to IT or technology jobs. The platform supports employment opportunities in:

- Retail / Store
- Sales
- Food Service / Restaurant
- Hospitality / Tourism
- Office / Administration
- Accounting / Finance
- Healthcare
- Education / Tutoring
- Construction / Skilled Trades
- Agriculture
- Fisheries
- Manufacturing
- Logistics / Delivery
- Transportation
- Automotive / Mechanical
- Customer Service
- Security
- General Labor
- IT / Technology
- Other

## Key Features

### For Job Seekers
- Profile with skills, education, experience, location and preferences, plus a matching-relevant completeness indicator
- Job search and filtering by municipality, barangay, category, skills, salary, employment type, schedule, distance and match band
- Geographic job map with PSGC barangay boundaries, job pins and near-me radius search
- Match percentage with a per-criterion explanation (skills, location, employment type, experience, salary)
- Recommended jobs, saved jobs
- Skills gap analysis, what-if simulation, local demand rankings, learning resources
- Applications with status tracking, timeline, interviews and withdrawal
- Notification inbox, reporting of suspicious postings

### For Employers
- Business profile and verification request
- Job posting with moderation status (`PENDING_REVIEW` before publication)
- Applicant list ranked by match score, applicant profiles with the criterion breakdown
- Status pipeline (`SUBMITTED → REVIEWING → SHORTLISTED → INTERVIEW → ACCEPTED/REJECTED`) and interview scheduling
- Hiring analytics: overview, funnel, response metrics, matching, job performance, geography, insights
- Date-range filtering and CSV export

### For Administrators
- Moderation dashboard and queues
- Employer verification, rejection and information requests
- Job approval, rejection, change requests and restoration
- User report handling and rule-based suspicious listing detection
- Account suspension and restoration (sessions stop immediately)
- Append-only audit log, skills analytics, learning-resource management

## Technology Stack

- Backend: Java 21, Spring Boot 3.4.2, Spring Security with JWT, Spring Data JPA, Flyway (V1–V9)
- Maps: Leaflet 1.9.4 with OpenStreetMap-compatible tiles, configured server-side
- Tests: 58 JUnit tests (`mvn test`, real PostgreSQL)
- Database: PostgreSQL 17 on Supabase (tables live in their own `enuv` schema)
- Frontend: HTML5, CSS3, JavaScript (Vanilla), served by the embedded Tomcat
- Font Awesome for icons, Springdoc OpenAPI for the API docs

## Installation

```bash
cp backend/.env.example backend/.env   # fill in the Supabase password and a JWT secret
cd backend
mvn spring-boot:run
```

Then open http://localhost:8080. Flyway creates the schema and tables on the first
start. See `RUN_INSTRUCTIONS.md` for details.

## Deployment

Production runs as a container plus Supabase Postgres. See
[DEPLOYMENT.md](DEPLOYMENT.md) for the required environment variables, the
Docker build and the custom-domain steps.

GitHub Pages cannot host this application: the pages and the `/api` endpoints
are served by the same JVM process.

## Usage

1. Start at the login page (`index.html`)
2. Choose to create either a Job Seeker or Employer account
3. Complete the registration form specific to your role
4. Log in to access your respective dashboard
5. Job seekers can search for jobs, view match percentages, and apply
6. Employers can post jobs, review applicants, and manage their listings
7. Administrators can manage users, job posts, and system settings

## Matching System

The platform uses one **deterministic weighted scoring engine** (`MatchingEngine`) — rule-based arithmetic, not AI:

| Criterion | Weight |
| --- | --- |
| Skills | 40% |
| Location (barangay / municipality / bordering municipality) | 25% |
| Employment type | 15% |
| Experience | 10% |
| Salary | 10% |

The overall percentage is `earned points / applicable weight × 100`. A criterion that cannot be judged from the stored data (no skills listed, salary not disclosed, no employment preference) is removed from the denominator, so missing data never counts as a mismatch or as free points. Bands: Excellent ≥ 90, Strong 75–89, Good 60–74, Partial 40–59, Low < 40.

The same engine scores the seeker's job list, the recommendations, the employer's applicant ranking and the admin statistics, and every score comes with a per-criterion explanation. See `docs/final-release/MATCHING_ENGINE.md`.

The match percentage is a compatibility indicator: the final hiring decision remains with the employer.

## Geographic Scope

The system is strictly limited to the following municipalities in Camarines Norte:
- Basud
- Daet
- Vinzons
- Labo

Barangays within these municipalities are available for more specific location targeting.

## Documentation

Final release documentation lives in [`docs/`](docs/):

- `docs/final-release/` — project overview, system architecture, database, matching engine, API, installation and deployment, testing and QA, security and limitations, release checklist, executive summary
- `docs/user-manuals/` — job seeker, employer and administrator manuals
- `docs/presentation/` — defense deck (`JSM_Final_Defense.pptx`), presenter script, defense Q&A, live demo guide, real screenshots

## Notes

- Every page listed above is wired to the API, including the admin console and
  the employer analytics with CSV export.
- Configuration comes from `backend/.env` (git-ignored) or the hosting
  environment. Never commit the database password or the JWT secret.
- `JSM_ADMIN_ACCESS_CODE` enables the optional `admin` / `admin++` sign-in
  shortcut; without it the shortcut is disabled and administrators sign in with
  their email and password.
- Rate limiting is persistent (table `rate_limit_counters`, migration V9) and
  configurable through `JSM_RATE_LIMIT_*`; see
  `docs/final-release/SECURITY_AND_LIMITATIONS.md`.
- Test suites live in `tools/` (`phase7_security_check.py`,
  `phase6_moderation_check.py`, `phase6_job_moderation_check.py`).

## License

MIT License