-- Flyway migration V3: demo accounts for the three roles.
--
-- The hashes below are real BCrypt hashes (cost 10), so these accounts can
-- actually sign in. They are demo credentials only; change them before any
-- real use.
--
--   job seeker : seeker@test.com   / jobseeker123
--   employer   : employer@test.com / employer123
--   admin      : signs in with the name "admin++" and no password
--                (the existing admin@jobskillsmatching.com account)

-- ---------------------------------------------------------------------------
-- Job seeker
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('seeker@test.com',
        '$2b$10$aCVllV1jPE/K2N1KpyHds.ft5oTiDiYtKcOq9uS1YksaN2cbalzGi',
        'JOB_SEEKER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO job_seeker_profiles
    (user_id, full_name, contact_number, municipality, barangay,
     education_level, course_field, work_experience, certifications,
     employment_type_preference, preferred_work_schedule, availability,
     expected_salary_min, expected_salary_max, skills)
SELECT u.id, 'Test Job Seeker', '09171234567', 'DAET', 'Test Barangay',
       'COLLEGE_GRADUATE', 'Computer Science', '2 years', 'Java, Spring Boot',
       'FULL_TIME', 'MORNING', 'IMMEDIATELY',
       50000.00, 80000.00, 'Java, Spring, SQL'
FROM users u
WHERE u.email = 'seeker@test.com'
ON CONFLICT (user_id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Employer
-- ---------------------------------------------------------------------------
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('employer@test.com',
        '$2b$10$L7oZhHJZUgy4ccKJrP1OnuX/BXi8gjGy0Y.J7aI8jC5ODJIDkFDMq',
        'EMPLOYER', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO employer_profiles
    (user_id, business_name, employer_name, contact_number, business_type,
     municipality, barangay, business_address, business_description, logo)
SELECT u.id, 'Test Company', 'Test Employer', '09177654321', 'IT_TECHNOLOGY',
       'DAET', 'Test Barangay', '123 Test Street, Daet',
       'A technology company providing test services.', NULL
FROM users u
WHERE u.email = 'employer@test.com'
ON CONFLICT (user_id) DO NOTHING;
