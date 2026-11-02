# How to Run the Job & Skills Matching System

## Prerequisites

1. **Java 21** - Required for Spring Boot 3.4.2
2. **Maven 3.9+** - Build tool
3. **A Supabase project** (PostgreSQL) - no local database server needed

## Quick Start

### 1. Install Prerequisites

**On Windows (using Chocolatey):**
```powershell
choco install openjdk21 maven
```

**On Windows (using Scoop):**
```powershell
scoop install openjdk21 maven
```

**On Linux/macOS:**
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install openjdk-21-jdk maven

# macOS (Homebrew)
brew install openjdk@21 maven
```

### 2. Configure the Database (Supabase)

1. Copy the template and fill in the real values:

```bash
cp backend/.env.example backend/.env
```

2. In the Supabase dashboard open **Project Settings -> Database** and copy the
   database password into `backend/.env`:

```
DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require&currentSchema=enuv
DB_USERNAME=postgres
DB_PASSWORD=<your-database-password>
JWT_SECRET=<a long random string>
```

`backend/.env` is git-ignored. Spring Boot reads it automatically, so you never
need to export variables by hand or commit a password.

3. Generate the JWT secret with `openssl rand -base64 48`. The application
   refuses to start without it - that is deliberate, so nobody can sign tokens
   with a key taken from this repository.

Nothing else to create: on first start Flyway creates the `enuv` schema and all
of its tables. They live in their own schema, so they do not touch any other
application's tables in the same database.

### 3. Build and Run Backend

```bash
cd backend
mvn spring-boot:run
```

The backend will start at `http://localhost:8080`

### 4. Access the Application

- **Frontend (Login Page):** http://localhost:8080/
- **Swagger API Docs:** http://localhost:8080/swagger-ui.html
- **API Base URL:** http://localhost:8080/api

### 5. Default Admin Account

The system creates a default admin on first run:
- **Email:** admin@jobskillsmatching.com
- **Password:** the seeded hash in `V1__create_tables.sql` is a public example value, so treat this account as a placeholder. Replace the hash with your own BCrypt hash before using the admin area for anything real.

## Frontend Development

The frontend files are served automatically by the embedded Tomcat from:
- `backend/src/main/resources/static/` (copied during build)

Source files are in:
- the repository root (`index.html`, `register.html`, the role folders)
- `assets/` (CSS, shared JavaScript, logo)

## Project Structure

```
job-skills-matching-system/
├── backend/                    # Spring Boot application
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/jobskills/
│   │   │   │   ├── config/     # Security, JWT, CORS
│   │   │   │   ├── controller/ # REST endpoints
│   │   │   │   ├── dto/        # Request/Response objects
│   │   │   │   ├── exception/  # Error handling
│   │   │   │   ├── model/      # JPA entities & enums
│   │   │   │   ├── repository/ # Data access
│   │   │   │   ├── service/    # Business logic
│   │   │   │   └── Application.java
│   │   │   └── resources/
│   │   │       ├── application.yml
│   │   │       ├── db/migration/  # Flyway scripts
│   │   │       └── static/        # Frontend files (served by Tomcat)
│   │   └── test/
│   └── pom.xml
├── assets/
│   ├── css/style.css          # Complete stylesheet
│   └── js/
│       ├── main.js            # Shared utilities
│       ├── layout.js          # Sidebar, bottom nav, toasts
│       └── api.js             # API client
├── job-seeker/                # Job seeker pages
├── employer/                  # Employer pages
├── admin/                     # Admin pages
├── index.html                 # Login page
├── register.html              # Account type selection
└── backend/.env.example       # Template for the database and JWT settings
```

## API Endpoints

### Authentication
- `POST /api/auth/login` - Login (returns JWT tokens)
- `POST /api/auth/register` - Register (job seeker or employer)

### Job Seeker
- `GET /api/job-seekers/profile` - Get profile
- `PUT /api/job-seekers/profile` - Update profile
- `GET /api/job-seekers/profile/completion` - Get completion %
- `GET /api/jobs` - Search jobs (public)
- `GET /api/jobs/with-match` - Search with match % (auth required)
- `GET /api/jobs/{id}` - Get job details
- `POST /api/job-seekers/apply` - Apply for job
- `GET /api/job-seekers/applications` - Get applications
- `POST /api/job-seekers/saved` - Save job
- `DELETE /api/job-seekers/saved/{jobId}` - Unsave job
- `GET /api/job-seekers/saved` - Get saved jobs

### Employer
- `GET /api/employers/profile` - Get business profile
- `PUT /api/employers/profile` - Update profile
- `POST /api/employers/jobs` - Post new job
- `PUT /api/employers/jobs/{id}` - Update job
- `DELETE /api/employers/jobs/{id}` - Delete job
- `GET /api/employers/my-jobs` - List employer's jobs
- `GET /api/employers/dashboard` - Dashboard stats
- `GET /api/employers/applicants?jobId=X` - List applicants
- `PUT /api/employers/applicants/{id}/status` - Update status

### Admin
- `GET /api/admin/dashboard` - System stats
- `GET /api/admin/users` - List users
- `GET /api/admin/employers` - List employers
- `PUT /api/admin/employers/{id}/verify` - Verify employer
- `PUT /api/admin/users/{id}/deactivate` - Deactivate user
- `GET /api/admin/job-posts` - All job posts
- `DELETE /api/admin/job-posts/{id}` - Delete job post
- `GET /api/admin/applications` - All applications
- `GET /api/admin/reports` - System reports

## Troubleshooting

### Port 8080 already in use
Change `server.port` in `application.yml`

### Database connection failed
- Check that `backend/.env` exists and holds the current Supabase password
- Use `sslmode=require` in `DB_URL`; Supabase rejects unencrypted connections
- If your network has no IPv6 route, use the session pooler host from
  Project Settings -> Database instead of `db.<project-ref>.supabase.co`
- Tables do not exist yet? Check the startup log for the Flyway migration line

### Flyway migration fails
- Check that the database accepts the migration (the logs name the failing statement)
- Ensure user has CREATE/DROP privileges

### Frontend not loading
- Run `mvn clean compile spring-boot:run` to copy static resources
- Check `target/classes/static/` for frontend files

## Testing

Run the matching-engine unit tests (no database needed):
```bash
cd backend && mvn test -Dtest=MatchingEngineTest
```

The `JobSkillsMatchingSystemApplicationTests` context test boots the whole
application, so it needs the variables from `backend/.env` to be set.

## Production Build

```bash
mvn clean package -DskipTests
java -jar target/job-skills-matching-system-1.0.0.jar
```