# Job & Skills Matching System - Backend

Spring Boot backend for the Job & Skills Matching System.

## Technology Stack

- Java 21
- Spring Boot 3.4.x
- Spring MVC
- Spring Data JPA
- Hibernate
- Spring Security with JWT
- Flyway
- MySQL
- Springdoc OpenAPI (Swagger UI)

## Setup

1. Ensure MySQL is running and create the database:
```sql
CREATE DATABASE job_skills_matching;
```

2. Configure database credentials in `src/main/resources/application.yml`

3. Build and run:
```bash
mvn spring-boot:run
```

4. Open Swagger UI:
```
http://localhost:8080/swagger-ui.html
```

## Authentication

The API uses JWT authentication. Include the token in the Authorization header:

```
Authorization: Bearer <token>
```

## API Endpoints

### Auth
- `POST /api/auth/login` - Login
- `POST /api/auth/register` - Register as job seeker or employer

### Job Seeker
- `GET /api/job-seekers/profile` - Get profile
- `PUT /api/job-seekers/profile` - Update profile
- `GET /api/job-seekers/profile/completion` - Get profile completion percentage
- `GET /api/job-seekers/jobs` - Search jobs
- `GET /api/job-seekers/jobs/{id}` - Get job details with match percentage
- `POST /api/job-seekers/apply` - Apply for a job
- `GET /api/job-seekers/applications` - Get applications
- `POST /api/job-seekers/saved` - Save a job
- `DELETE /api/job-seekers/saved/{jobId}` - Unsave a job
- `GET /api/job-seekers/saved` - Get saved jobs

### Employer
- `GET /api/employers/profile` - Get profile
- `PUT /api/employers/profile` - Update profile
- `GET /api/employers/dashboard` - Get dashboard stats
- `POST /api/employers/jobs` - Post a job
- `PUT /api/employers/jobs/{id}` - Update a job
- `DELETE /api/employers/jobs/{id}` - Delete a job
- `GET /api/employers/my-jobs` - Get my jobs
- `GET /api/employers/applicants?jobId=1` - Get applicants
- `PUT /api/employers/applicants/{id}/status?status=SHORTLISTED` - Update status

### Admin
- `GET /api/admin/dashboard` - Get dashboard stats
- `GET /api/admin/users` - Get all users
- `GET /api/admin/employers` - Get all employers
- `GET /api/admin/job-posts` - Get all job posts
- `GET /api/admin/applications` - Get all applications
- `GET /api/admin/reports` - Get system reports
- `GET /api/admin/municipalities` - Get municipalities
- `GET /api/admin/categories` - Get job categories

## Matching Engine

The matching engine uses a rule-based algorithm with the following weights:

- Skills: 25%
- Education: 15%
- Experience: 20%
- Certifications: 10%
- Location: 10%
- Availability: 10%
- Employment Type: 5%
- Salary: 5%

The matching logic is implemented in `com.jobskills.service.MatchingEngine`.

## Testing

```bash
mvn test
```

## File Uploads

Profile photos and business logos are stored in the `uploads/` directory.
Maximum file size: 5MB.
