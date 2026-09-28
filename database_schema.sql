-- Job & Skills Matching System - Database Schema
-- This SQL schema shows the database structure for a production implementation

-- Create database
CREATE DATABASE IF NOT EXISTS job_skills_matching;
USE job_skills_matching;

-- Users table (base for both job seekers and employers)
CREATE TABLE users (
    id INT PRIMARY KEY AUTO_INCREMENT,
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    user_type ENUM('job_seeker', 'employer', 'admin') NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    is_verified BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

-- Job seeker profiles
CREATE TABLE job_seeker_profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT UNIQUE NOT NULL,
    full_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20),
    municipality ENUM('Basud', 'Daet', 'Vinzons', 'Labo') NOT NULL,
    barangay VARCHAR(100),
    education_level VARCHAR(100),
    course_field VARCHAR(100),
    work_experience TEXT,
    certifications TEXT,
    employment_type_preference ENUM('Part-time', 'Full-time', 'Contract', 'Temporary', 'Any'),
    preferred_work_schedule ENUM('Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible', 'Any'),
    availability ENUM('Immediately', 'Within 1 week', 'Within 2 weeks', 'Within 1 month', 'Within 3 months', 'More than 3 months'),
    expected_salary_min DECIMAL(10, 2),
    expected_salary_max DECIMAL(10, 2),
    profile_photo VARCHAR(255),
    skills TEXT, -- Comma-separated or JSON array
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Employer profiles
CREATE TABLE employer_profiles (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT UNIQUE NOT NULL,
    business_name VARCHAR(255) NOT NULL,
    employer_name VARCHAR(255) NOT NULL,
    contact_number VARCHAR(20),
    business_type ENUM(
        'Retail / Store', 'Restaurant / Food Service', 'Hospitality',
        'Office / Administration', 'Healthcare', 'Education',
        'Construction', 'Agriculture', 'Fisheries', 'Manufacturing',
        'Logistics / Delivery', 'Transportation', 'Automotive',
        'Security', 'IT / Technology', 'Other'
    ) NOT NULL,
    municipality ENUM('Basud', 'Daet', 'Vinzons', 'Labo') NOT NULL,
    barangay VARCHAR(100),
    business_address TEXT,
    business_description TEXT,
    logo VARCHAR(255),
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- Job postings
CREATE TABLE job_posts (
    id INT PRIMARY KEY AUTO_INCREMENT,
    employer_id INT NOT NULL,
    job_title VARCHAR(255) NOT NULL,
    job_category ENUM(
        'Retail / Store', 'Sales', 'Food Service / Restaurant',
        'Hospitality / Tourism', 'Office / Administration',
        'Accounting / Finance', 'Healthcare', 'Education / Tutoring',
        'Construction / Skilled Trades', 'Agriculture', 'Fisheries',
        'Manufacturing', 'Logistics / Delivery', 'Transportation',
        'Automotive / Mechanical', 'Customer Service', 'Security',
        'General Labor', 'IT / Technology', 'Other'
    ) NOT NULL,
    job_description TEXT NOT NULL,
    responsibilities TEXT NOT NULL,
    required_skills TEXT, -- Comma-separated or JSON array
    education_requirement ENUM(
        'No Formal Education', 'Elementary Graduate', 'High School Graduate',
        'Vocational/Technical', 'College Graduate', 'Post Graduate', 'Any'
    ),
    experience_requirement ENUM(
        'No Experience', '1-2 Years', '3-5 Years', '5+ Years', 'Any'
    ),
    certifications TEXT, -- Comma-separated or JSON array
    salary_min DECIMAL(10, 2),
    salary_max DECIMAL(10, 2),
    employment_type ENUM('Part-time', 'Full-time', 'Contract', 'Temporary'),
    work_schedule ENUM('Morning', 'Afternoon', 'Evening', 'Night', 'Weekend', 'Flexible'),
    available_positions INT DEFAULT 1,
    municipality ENUM('Basud', 'Daet', 'Vinzons', 'Labo') NOT NULL,
    barangay VARCHAR(100),
    job_location VARCHAR(255),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (employer_id) REFERENCES employer_profiles(id) ON DELETE CASCADE
);

-- Job applications
CREATE TABLE job_applications (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_seeker_id INT NOT NULL,
    job_post_id INT NOT NULL,
    applied_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    application_status ENUM(
        'Applied', 'Under Review', 'Shortlisted', 'Interview', 'Accepted', 'Rejected'
    ) DEFAULT 'Applied',
    match_percentage DECIMAL(5, 2), -- Calculated match percentage
    FOREIGN KEY (job_seeker_id) REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE,
    UNIQUE KEY unique_application (job_seeker_id, job_post_id)
);

-- Saved jobs (job seeker bookmarks)
CREATE TABLE saved_jobs (
    id INT PRIMARY KEY AUTO_INCREMENT,
    job_seeker_id INT NOT NULL,
    job_post_id INT NOT NULL,
    saved_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (job_seeker_id) REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    FOREIGN KEY (job_post_id) REFERENCES job_posts(id) ON DELETE CASCADE,
    UNIQUE KEY unique_saved_job (job_seeker_id, job_post_id)
);

-- System activity log
CREATE TABLE activity_log (
    id INT PRIMARY KEY AUTO_INCREMENT,
    user_id INT,
    action_type VARCHAR(100) NOT NULL,
    description TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Indexes for performance
CREATE INDEX idx_job_posts_municipality ON job_posts(municipality);
CREATE INDEX idx_job_posts_category ON job_posts(job_category);
CREATE INDEX idx_job_posts_active ON job_posts(is_active);
CREATE INDEX idx_job_applications_status ON job_applications(application_status);
CREATE INDEX idx_job_seeker_municipality ON job_seeker_profiles(municipality);
CREATE INDEX idx_employer_municipality ON employer_profiles(municipality);

-- Sample data for municipalities
INSERT INTO users (email, password_hash, user_type, is_active, is_verified) VALUES
('admin@jobskillsmatching.com', '$2b$10$hashedpasswordhere', 'admin', TRUE, TRUE);

-- Note: In a real application, passwords would be properly hashed using bcrypt or similar