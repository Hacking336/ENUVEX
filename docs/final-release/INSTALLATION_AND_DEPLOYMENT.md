# Installation and Deployment Guide

**JSM — final release.** Every value below is a placeholder. No real password, JWT secret, database URL with credentials or administrator access code appears in this document or anywhere in the repository.

---

## 1. Requirements

| Requirement | Version used | Notes |
| --- | --- | --- |
| JDK | 21 | `java.version=21`; the Docker image uses `eclipse-temurin-21` |
| Maven | 3.9.x | `mvn -v`; the Dockerfile uses `maven:3.9-eclipse-temurin-21` |
| PostgreSQL | 16 or newer (17 in production) | Local install or Supabase |
| Git | any recent | |
| Browser | current Chrome/Firefox/Safari/Edge | The map needs internet access for Leaflet and tiles |
| Docker | optional, only for the container workflow | |

Nothing else is required: there is no Node.js, npm, bundler or database CLI dependency for running the app.

## 2. Get the code

```bash
git clone https://github.com/Hacking336/ENUVEX.git
cd ENUVEX
```

Repository layout (only what matters for a run):

```
backend/                Spring Boot application (pom.xml, src/main/java, src/main/resources)
  src/main/resources/db/migration/   Flyway V1–V9
  src/main/resources/geo/            PSGC-aligned GeoJSON for the four municipalities
assets/                 shared CSS, JS (api.js, role-app.js, jsm-map.js…), brand files, images
job-seeker/ employer/ admin/         static pages per role
index.html login.html register.html layout.html
Dockerfile              multi-stage build (Maven → JRE)
```

## 3. Database

1. Create an empty database (local or Supabase):

```sql
CREATE DATABASE jsm;
```

2. Nothing else needs to be created by hand. On first start Flyway creates the `enuv` schema and every table, then Hibernate validates the result.

**Do not run `flyway clean`.** The application sets `clean-disabled: true` on purpose; a manual clean would drop every table in the schema.

## 4. Environment variables

Create `backend/.env` (git-ignored) for local runs, or set the same names in the hosting dashboard.

```properties
DB_URL=jdbc:postgresql://localhost:5432/jsm?currentSchema=enuv
DB_USERNAME=<your-db-user>
DB_PASSWORD=<your-db-password>
JWT_SECRET=<at-least-32-random-characters>
```

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `DB_URL` | yes | `jdbc:postgresql://localhost:5432/postgres?currentSchema=enuv` | JDBC URL; `currentSchema=enuv` keeps this app's tables separate |
| `DB_USERNAME` | yes | `postgres` | |
| `DB_PASSWORD` | yes | — | |
| `JWT_SECRET` | **yes** | none on purpose | HS256 signing key; the app refuses to start without it |
| `JWT_EXPIRATION_MS` | no | `3600000` (1 h) | Access-token lifetime |
| `JWT_REFRESH_EXPIRATION_MS` | no | `604800000` | Value carried in the token model; there is no refresh endpoint |
| `PORT` | no | `8080` | Hosting platforms inject this |
| `JSM_ADMIN_ACCESS_CODE` | no | empty | Enables the `admin` / `admin++` sign-in shortcut. **Empty = shortcut disabled**; administrators then sign in by email and password |
| `JSM_MAP_TILE_URL` | no | OpenStreetMap tile server | Any compatible tile provider |
| `JSM_MAP_ATTRIBUTION`, `JSM_MAP_MIN_ZOOM`, `JSM_MAP_MAX_ZOOM`, `JSM_MAP_CENTER_LAT`, `JSM_MAP_CENTER_LNG`, `JSM_MAP_ZOOM` | no | see `application.yml` | Map defaults |
| `JSM_RATE_LIMIT_ENABLED` | no | `true` | Master switch |
| `JSM_RATE_LIMIT_LOGIN`, `_LOGIN_WINDOW` | no | `20`, `60` | Sign-in limit per address |
| `JSM_RATE_LIMIT_REGISTRATION`, `_WINDOW` | no | `10`, `3600` | Registration limit |
| `JSM_RATE_LIMIT_REPORTS`, `_WINDOW` | no | `10`, `3600` | Report submission limit |
| `JSM_RATE_LIMIT_APPLICATIONS`, `_WINDOW` | no | `30`, `3600` | Job application limit |

Generate a secret with `openssl rand -base64 48`. Never commit `.env`, and never paste a real value into documentation or issues.

## 5. Run locally

```bash
cd backend
mvn spring-boot:run
```

or build once and run the jar:

```bash
mvn -q package -DskipTests
java -jar target/job-skills-matching-system-1.0.3.jar
```

The Maven build copies the HTML/CSS/JS from the repository root into the jar, so **the jar always serves the frontend too** — nothing else has to be started.

Open <http://localhost:8080>:

- `/` landing page, `login.html`, `register.html`
- `/job-seeker/…`, `/employer/…`, `/admin/…`
- `GET /api/health` → `{"status":"UP","database":"UP"}`
- Swagger UI at `/swagger-ui.html`

First start log lines that confirm a healthy setup:

```
Flyway … Migrating schema "enuv" to version "9 - rate limit counters"
Flyway … Successfully applied N migrations
Tomcat started on port 8080
```

## 6. Flyway behaviour

- Location: `classpath:db/migration`; schema `enuv`; `baseline-on-migrate: true` with `baseline-version: 0`.
- Every migration is additive and re-runnable (`IF NOT EXISTS`, guarded `ALTER`), so a start on an already-migrated database applies nothing.
- A migration that fails stops the application before it accepts traffic — this is deliberate.
- Data changes performed by migrations: V6 renames two status values and backfills one history row per application; V8 backfills employer verification and sets existing posts to `APPROVED`. Nothing is deleted.

## 7. Tests

```bash
cd backend
mvn test
```

- The suite needs the same `DB_URL`/`DB_USERNAME`/`DB_PASSWORD`/`JWT_SECRET` variables, because `contextLoads` starts the full application context and applies migrations to that database.
- Use a dedicated test database, not production.
- Expected result for this release: **58 tests, 0 failures** (see `TESTING_AND_QA_REPORT.md`).

Scripted HTTP suites (they need a running instance):

```bash
python3 tools/phase7_security_check.py          # JSM_BASE, JSM_ADMIN_CODE
python3 tools/phase6_moderation_check.py
python3 tools/phase6_job_moderation_check.py
```

## 8. Docker

```bash
docker build -t jsm .

docker run --rm -p 8080:8080 \
  -e DB_URL="jdbc:postgresql://<host>:5432/<db>?sslmode=require&currentSchema=enuv" \
  -e DB_USERNAME="<user>" \
  -e DB_PASSWORD="<password>" \
  -e JWT_SECRET="<secret>" \
  jsm
```

The build context must be the repository root (`docker build .`), because the POM copies sibling directories into the jar.

## 9. Render deployment (the workflow actually used)

1. Push to `main`. Render is configured to auto-deploy this branch.
2. Render builds the `Dockerfile`: Maven compiles and packages the jar, then a JRE image runs it.
3. Environment variables are set in the Render dashboard (Service → Environment). At minimum `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`; recommended: `JSM_ADMIN_ACCESS_CODE` and the rate-limit values.
4. Health check path: `/api/health`.
5. Watch the deploy log until the status is **live**, then open the health endpoint and a page.

Equivalent CLI checks (Render dashboard also shows this):

```bash
curl -s https://<service>.onrender.com/api/health
# {"status":"UP","database":"UP"}
```

Notes specific to this deployment:

- Supabase requires `sslmode=require` and, from networks without IPv6, the pooler hostname with username `postgres.<project-ref>`.
- The free instance sleeps when idle; the first request afterwards takes a few seconds. The Render service env var `RENDER_UPDATE_ENV_VAR` can generate values (`generateValue: true`), which is how `JSM_ADMIN_ACCESS_CODE` was created without ever writing it down.
- Uploads live in PostgreSQL, so no persistent volume is needed and images survive redeploys.

## 10. Custom domain

1. Add the domain to the Render service.
2. Create the DNS record Render shows (CNAME for a subdomain, A/ALIAS for an apex).
3. Enable HTTPS once the certificate is issued.
4. The API accepts the new host automatically: CORS treats the request host as allowed, so same-host POSTs keep working without editing code.

## 11. Troubleshooting

| Symptom | Cause | Fix |
| --- | --- | --- |
| App exits at start: `Could not resolve placeholder 'JWT_SECRET'` | Variable missing | Set `JWT_SECRET` (≥ 32 chars) |
| `Schema-validation: missing table` | Started against a database Flyway has not migrated (wrong schema/user) | Check `DB_URL` includes `currentSchema=enuv` and the user may create schemas |
| `Connection refused` / timeout | Postgres not running, wrong port, or Supabase IPv6-only host | Start Postgres; use the pooler hostname if IPv6 is unavailable |
| `password authentication failed` | Wrong `DB_PASSWORD` or user | Re-check the dashboard values |
| `PreparedStatement`/`SSL` error on startup | Supabase requires TLS | Add `sslmode=require` |
| Pages load, every API call returns 401 | Signed out or expired token (1 h) | Sign in again |
| `403 Invalid CORS request` on login | Origin host differs from the host the page was loaded from (proxy rewriting `Host`) | Load the page and the API from the same hostname, or add the origin to `FIXED_ORIGINS` |
| Login works, then 429 quickly | Rate limit reached from that network (shared IP) | Wait for `Retry-After`, or raise `JSM_RATE_LIMIT_LOGIN` |
| Map is grey | No internet access to unpkg/tiles, or a blocked provider | Check the console; the rest of the app keeps working |
| Jobs do not appear on the map | Posts have no confirmed workplace pin | Edit the post and pick a barangay/point on the map |
| Admin login `admin++` refused | `JSM_ADMIN_ACCESS_CODE` not set, or the code was typed in the email field | Put the code in the password field, or sign in with the admin email and password |
| Newly posted job is invisible to seekers | Moderation status is `PENDING_REVIEW` | Sign in as administrator and approve it |
| `mvn test` fails on `contextLoads` | No database, or variables missing | Provide the four variables; use a throwaway database |
| `mvn` not found | Maven not installed | Install Maven 3.9+ or use the Docker build |
