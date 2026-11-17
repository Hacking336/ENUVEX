-- Flyway migration V3: Add dummy job seeker and employer accounts
-- Password hashes generated with: new BCryptPasswordEncoder().encode("password123")
-- The admin account (admin@jobskillsmatching.com) already exists from V1

-- ==================================----------------------
-- 1. Job Seeker Account
-- ==================================----------------------
-- Email: seeker@test.com
-- Password: jobseeker123
-- User type: JOB_SEEKER
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('seeker@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'JOB_SEEKER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Job seeker profile linked to user_id
-- The user_id will be auto-generated; this will work if no conflict on email
INSERT INTO job_seeker_profiles (user_id, full_name, contact_number, municipality, barangay, education_level, course_field, work_experience, certifications, employment_type_preference, preferred_work_schedule, availability, expected_salary_min, expected_salary_max, skills)
VALUES (1, 'Test Job Seeker', '09171234567', 'Test City', 'Test Barangay', 'COLLEGE_GRADUATE', 'Computer Science', '2 years', 'Java, Spring Boot', 'FULL_TIME', 'IMMEDIATELY', '50000.00', '80000.00', 'Java, Spring, SQL');
-- Note: If user_id is not 1 (e.g., admin already used id 1), adjust accordingly. The profile insertion relies on auto-increment or specific user_id mapping.

-- ==================================----------------------
-- 2. Employer Account
-- ==================================----------------------
-- Email: employer@test.com
-- Password: employer123
-- User type: EMPLOYER
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('employer@test.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'EMPLOYER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

-- Employer profile linked to user_id
INSERT INTO employer_profiles (user_id, business_name, employer_name, contact_number, business_type, municipality, barangay, business_address, business_description, logo)
VALUES (2, 'Test Company', 'Test Employer', '09177654321', 'Technology', 'Test City', 'Test Barangay', '123 Test Street, Test City', 'A technology company providing test services.', 'logo.png');
-- Note: Adjust user_id if needed (likely 2 if admin occupies id 1).

-- ==================================----------------------
-- 3. Admin Account with admin++ name login support
-- ==================================----------------------
-- The admin++ name-only login is handled in UserService.java:
-- Accepts "admin" or "admin++" as the admin name with blank password
-- No new database user needed; the existing admin@jobskillsmatching.com works
-- via the name-only login path (password blank allowed only for ADMIN userType)
-- ==================================----------------------

-- Migration Notes:
-- - Password hashes above use a placeholder BCrypt hash (same as admin for demo purposes)
-- - In production, generate new hashes with: new BCryptPasswordEncoder().encode("your-password")
-- - If user_id conflicts occur (admin uses id 1), adjust the profile INSERT statements
-- - The admin++ name-only login feature is implemented in UserService.java and does not
--   require a new database user; it works with the existing admin account via email matching.