-- Flyway migration V3: Add dummy job seeker and employer accounts
-- NOTE: Password hashes must be generated with: new BCryptPasswordEncoder().encode("password")
-- The admin account uses a placeholder hash from the repository; dummy accounts need proper hashes.
-- After deploying, generate new hashes and update this migration or update the .env passwords.

-- ==================================----------------------
-- 1. Job Seeker Account
-- ==================================----------------------
-- Email: seeker@test.com
-- Password: jobseeker123 (hash must be generated with BCryptPasswordEncoder)
-- User type: JOB_SEEKER
-- PASSWORD HASH MUST BE GENERATED: new BCryptPasswordEncoder().encode("jobseeker123")
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('seeker@test.com', '$2a$10$PLACEHOLDER_BCRYPT_HASH_FOR_jobseeker123', 'JOB_SEEKER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Job seeker profile linked to user_id
INSERT INTO job_seeker_profiles (user_id, full_name, contact_number, municipality, barangay, education_level, course_field, work_experience, certifications, employment_type_preference, preferred_work_schedule, availability, expected_salary_min, expected_salary_max, skills)
VALUES (1, 'Test Job Seeker', '09171234567', 'Test City', 'Test Barangay', 'COLLEGE_GRADUATE', 'Computer Science', '2 years', 'Java, Spring Boot', 'FULL_TIME', 'IMMEDIATELY', '50000.00', '80000.00', 'Java, Spring, SQL');

-- ==================================----------------------
-- 2. Employer Account
-- ==================================----------------------
-- Email: employer@test.com
-- Password: employer123 (hash must be generated with BCryptPasswordEncoder)
-- User type: EMPLOYER
-- PASSWORD HASH MUST BE GENERATED: new BCryptPasswordEncoder().encode("employer123")
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('employer@test.com', '$2a$10$PLACEHOLDER_BCRYPT_HASH_FOR_employer123', 'EMPLOYER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Employer profile linked to user_id
INSERT INTO employer_profiles (user_id, business_name, employer_name, contact_number, business_type, municipality, barangay, business_address, business_description, logo)
VALUES (2, 'Test Company', 'Test Employer', '09177654321', 'Technology', 'Test City', 'Test Barangay', '123 Test Street, Test City', 'A technology company providing test services.', 'logo.png');

-- ==================================----------------------
-- 3. Admin Account (existing from V1 migration)
-- ==================================----------------------
-- Admin++ name-only login is supported in UserService.java:
-- Accepts "admin" or "admin++" as the name with blank password
-- Works with existing admin@jobskillsmatching.com account
-- No changes needed to database; admin credentials in backend/.env

-- Migration Instructions:
-- 1. Deploy this migration to create the user accounts
-- 2. Generate proper BCrypt hashes: new BCryptPasswordEncoder().encode("jobseeker123") and new BCryptPasswordEncoder().encode("employer123")
-- 2. Update the password_hash values in this migration with the generated hashes
-- 3. Or update the backend/.env ADMIN_PASSWORD if needed
-- 4. Redeploy the application
-- 5. Test logins:
--    - seeker@test.com / jobseeker123 → JWT userType: JOB_SEEKER
--    - employer@test.com / employer123 → JWT userType: EMPLOYER
--    - admin++ / blank password → JWT userType: ADMIN (name-only login)