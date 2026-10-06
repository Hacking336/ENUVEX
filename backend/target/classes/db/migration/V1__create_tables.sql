-- Flyway migration V1: Create all tables
CREATE TABLE IF NOT EXISTS users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS job_seeker_profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT UNIQUE NOT NULL,
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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS employer_profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT UNIQUE NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    employer_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20),
    business_type VARCHAR(100) NOT NULL,
    municipality VARCHAR(50) NOT NULL,
    barangay VARCHAR(100),
    business_address TEXT,
    business_description TEXT,
    logo VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_posts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    employer_id INT NOT NULL,
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
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (employer_id) REFERENCES employer_profiles(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS job_applications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_seeker_id INT NOT NULL,
    job_post_id INT NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    application_status VARCHAR(50) DEFAULT 'APPLIED',
    match_percentage DECIMAL(5,2),
    FOREIGN KEY (job_seeker_id) REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE,
    UNIQUE KEY unique_application (job_seeker_id, job_post_id)
);

CREATE TABLE IF NOT EXISTS saved_jobs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_seeker_id INT NOT NULL,
    job_post_id INT NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_seeker_id) REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE,
    UNIQUE KEY unique_saved_job (job_seeker_id, job_post_id)
);

CREATE TABLE IF NOT EXISTS notifications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT NOT NULL,
    message TEXT NOT NULL,
    is_read BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS activity_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    action_type VARCHAR(100) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_job_posts_municipality ON job_posts(municipality);
CREATE INDEX idx_job_posts_category ON job_posts(job_category);
CREATE INDEX idx_job_posts_active ON job_posts(is_active);
CREATE INDEX idx_job_applications_status ON job_applications(application_status);
CREATE INDEX idx_job_seeker_municipality ON job_seeker_profiles(municipality);
CREATE INDEX idx_employer_municipality ON employer_profiles(municipality);

INSERT INTO users (email, password_hash, user_type, is_active, is_verified)
VALUES ('admin@jobskillsmatching.com', '$2a$10$N9qo8uLOickgx2ZMRZoMye.IyFmH4h5JtG4vR9f5K5m5L5J5L5J5L', 'ADMIN', TRUE, TRUE);