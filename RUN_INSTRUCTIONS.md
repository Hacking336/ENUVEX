# How to Run the Job & Skills Matching System

## Prerequisites

1. **Java 21** - Required for Spring Boot 3.4.2
2. **Maven 3.9+** - Build tool
3. **MySQL 8.0+** - Database

## Quick Start

### 1. Install Prerequisites

**On Windows (using Chocolatey):**
```powershell
choco install openjdk21 maven mysql
```

**On Windows (using Scoop):**
```powershell
scoop install openjdk21 maven mysql
```

**On Linux/macOS:**
```bash
# Ubuntu/Debian
sudo apt update && sudo apt install openjdk-21-jdk maven mysql-server

# macOS (Homebrew)
brew install openjdk@21 maven mysql
```

### 2. Configure Database

Start MySQL and create the database:

```sql
CREATE DATABASE job_skills_matching CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

Update `backend/src/main/resources/application.yml` with your MySQL credentials:

```yaml
spring.datasource.url=jdbc:mysql://localhost:3306/job_skills_matching?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=your_password_here
```

### 3. Build and Run Backend

```bash
cd c:/ENUVEX/job-skills-matching-system/backend
mvn clean compile spring-boot:run
```

The backend will start at `http://localhost:8080`

### 4. Access the Application

- **Frontend (Login Page):** http://localhost:8080/
- **Swagger API Docs:** http://localhost:8080/swagger-ui.html
- **API Base URL:** http://localhost:8080/api

### 5. Default Admin Account

The system creates a default admin on first run:
- **Email:** admin@jobskillsmatching.com
- **Password:** (check the Flyway migration V1__create_tables.sql for the BCrypt hash)

## Frontend Development

The frontend files are served automatically by the embedded Tomcat from:
- `backend/src/main/resources/static/` (copied during build)

Source files are in:
- `c:/ENUVEX/job-skills-matching-system/` (root)
- `c:/ENUVEX/job-skills-matching-system/assets/`

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
└── database_schema.sql        # Full MySQL schema
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
- Ensure MySQL is running: `systemctl status mysql` or `brew services list`
- Check credentials in `application.yml`
- Ensure database exists: `CREATE DATABASE job_skills_matching;`

### Flyway migration fails
- Check MySQL version compatibility
- Ensure user has CREATE/DROP privileges

### Frontend not loading
- Run `mvn clean compile spring-boot:run` to copy static resources
- Check `target/classes/static/` for frontend files

## Testing

Run unit tests:
```bash
mvn test
```

## Production Build

```bash
mvn clean package -DskipTests
java -jar target/job-skills-matching-system-1.0.0.jar
```