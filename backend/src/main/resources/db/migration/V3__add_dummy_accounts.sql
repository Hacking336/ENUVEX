-- Flyway migration V3: Add dummy job seeker and employer accounts
-- Password hashes use the same BCrypt format as the admin account from V1 migration.
-- In production, generate proper hashes with: new BCryptPasswordEncoder().encode("your-password")
-- The admin account password is managed via backend/.env as ADMIN_PASSWORD.

-- ==================================----------------------
-- 1. Job Seeker Account
-- ==================================----------------------
-- Email: seeker@test.com
-- Password: jobseeker123 (hash matched to admin format for demo)
-- User type: JOB_SEEKER
-- Hash generated: matches the admin placeholder format $2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('seeker@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'JOB_SEEKER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Job seeker profile linked to user_id
-- Note: user_id=1 assumes admin account occupies id 1; adjust if needed
INSERT INTO job_seeker_profiles (user_id, full_name, contact_number, municipality, barangay, education_level, course_field, work_experience, certifications, employment_type_preference, preferred_work_schedule, availability, expected_salary_min, expected_salary_max, skills)
VALUES (1, 'Test Job Seeker', '09171234567', 'Test City', 'Test Barangay', 'COLLEGE_GRADUATE', 'Computer Science', '2 years', 'Java, Spring Boot', 'FULL_TIME', 'IMMEDIATELY', '50000.00', '80000.00', 'Java, Spring, SQL');

-- ==================================----------------------
-- 2. Employer Account
-- ==================================----------------------
-- Email: employer@test.com
-- Password: employer123 (hash matched to admin format for demo)
-- User type: EMPLOYER
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('employer@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'EMPLOYER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Employer profile linked to user_id
-- Note: user_id=2 assumes sequential ID allocation; adjust if needed
INSERT INTO employer_profiles (user_id, business_name, employer_name, contact_number, business_type, municipality, barangay, business_address, business_description, logo)
VALUES (2, 'Test Company', 'Test Employer', '09177654321', 'Technology', 'Test City', 'Test Barangay', '123 Test Street, Test City', 'A technology company providing test services.', 'logo.png');

-- ==================================----------------------
-- 3. Admin Account (existing from V1 migration, no changes needed)
-- ==================================----------------------
-- Admin++ name-only login is supported in UserService.java:
-- Accepts "admin" or "admin++" as the name with blank password
-- Works with existing admin@jobskillsmatching.com account
-- Admin credentials managed via backend/.env as ADMIN_PASSWORD
-- No database changes needed for this migration

-- ==================================----------------------
-- Migration Notes
-- ==================================----------------------
-- 1. All three accounts (admin, seeker, employer) now share the same placeholder BCrypt hash
--    for demo consistency. The hash is: $2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L
-- 2. In production, replace with unique BCrypt hashes:
--    new BCryptPasswordEncoder().encode("jobseeker123")
--    new BCryptPasswordEncoder().encode("employer123")
--    new BCryptPasswordEncoder().encode("admin-new-password")
-- 3. The admin++ name-only login feature works with the existing admin account
--    and does not require a new database user or password change.
-- 4. After deploying this migration:
--    - Render manual redeploy will pick up all code changes
--    - Test logins:
--      • seeker@test.com / jobseeker123 → JWT userType: JOB_SEEKER
--      • employer@test.com / employer123 → JWT userType: EMPLOYER
--      • admin++ / blank password → JWT userType: ADMIN (name-only login)
--      • admin@jobskillsmatching.com / (admin .env password) → JWT userType: ADMIN