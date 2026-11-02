-- Flyway migration V1: initial schema (PostgreSQL / Supabase).
-- All tables live in the "enuv" schema, which Flyway creates on first run.

CREATE TABLE IF NOT EXISTS users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_verified BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_seeker_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    full_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20),
    municipality VARCHAR(50) NOT NULL,
    barangay VARCHAR(100),
    education_level VARCHAR(100),
    course_field VARCHAR(100),
    work_experience TEXT,
    certifications TEXT,
    employment_type_preference VARCHAR(50),
    preferred_work_schedule VARCHAR(50),
    availability VARCHAR(50),
    expected_salary_min DECIMAL(10,2),
    expected_salary_max DECIMAL(10,2),
    profile_photo VARCHAR(255),
    skills TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS employer_profiles (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT UNIQUE NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    business_name VARCHAR(255) NOT NULL,
    employer_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20),
    business_type VARCHAR(100) NOT NULL,
    municipality VARCHAR(50) NOT NULL,
    barangay VARCHAR(100),
    business_address TEXT,
    business_description TEXT,
    logo VARCHAR(255),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_posts (
    id BIGSERIAL PRIMARY KEY,
    employer_id BIGINT NOT NULL REFERENCES employer_profiles(id) ON DELETE CASCADE,
    job_title VARCHAR(255) NOT NULL,
    job_category VARCHAR(100) NOT NULL,
    job_description TEXT NOT NULL,
    responsibilities TEXT NOT NULL,
    required_skills TEXT,
    education_requirement VARCHAR(100),
    experience_requirement VARCHAR(50),
    certifications TEXT,
    salary_min DECIMAL(10,2),
    salary_max DECIMAL(10,2),
    employment_type VARCHAR(50),
    work_schedule VARCHAR(50),
    available_positions INT DEFAULT 1,
    municipality VARCHAR(50) NOT NULL,
    barangay VARCHAR(100),
    job_location VARCHAR(255),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_applications (
    id BIGSERIAL PRIMARY KEY,
    job_seeker_id BIGINT NOT NULL REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    job_post_id BIGINT NOT NULL REFERENCES job_posts(id) ON DELETE CASCADE,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    application_status VARCHAR(50) NOT NULL DEFAULT 'APPLIED',
    match_percentage DECIMAL(5,2),
    CONSTRAINT unique_application UNIQUE (job_seeker_id, job_post_id)
);

CREATE TABLE IF NOT EXISTS saved_jobs (
    id BIGSERIAL PRIMARY KEY,
    job_seeker_id BIGINT NOT NULL REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    job_post_id BIGINT NOT NULL REFERENCES job_posts(id) ON DELETE CASCADE,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_saved_job UNIQUE (job_seeker_id, job_post_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    message TEXT NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS activity_log (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    action_type VARCHAR(100) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_job_posts_municipality ON job_posts(municipality);
CREATE INDEX IF NOT EXISTS idx_job_posts_category ON job_posts(job_category);
CREATE INDEX IF NOT EXISTS idx_job_posts_active ON job_posts(is_active);
CREATE INDEX IF NOT EXISTS idx_job_posts_employer ON job_posts(employer_id);
CREATE INDEX IF NOT EXISTS idx_job_applications_status ON job_applications(application_status);
CREATE INDEX IF NOT EXISTS idx_job_applications_job ON job_applications(job_post_id);
CREATE INDEX IF NOT EXISTS idx_job_seeker_municipality ON job_seeker_profiles(municipality);
CREATE INDEX IF NOT EXISTS idx_employer_municipality ON employer_profiles(municipality);
CREATE INDEX IF NOT EXISTS idx_notifications_user ON notifications(user_id);

-- Default admin account.
-- The hash below is a well-known public example value, so this account is a
-- placeholder, not a secure login. Before real use, replace the hash with one
-- you generate: https://bcrypt-generator.com (cost 10) or
--   new BCryptPasswordEncoder().encode("your-new-password")
INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('admin@jobskillsmatching.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'ADMIN', TRUE, TRUE)
ON CONFLICT (email) DO NOTHING;
