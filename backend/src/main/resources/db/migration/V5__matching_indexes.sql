-- Flyway migration V5: indexes for the matching and filtering columns.
--
-- Matching reads job requirements, location and employment type, and the search
-- filters group by the same columns. These indexes keep those reads from turning
-- into table scans as the number of posts grows. Nothing is rewritten: indexes
-- are additive and safe to roll back with DROP INDEX.

CREATE INDEX IF NOT EXISTS idx_job_posts_active_created ON job_posts(is_active, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_job_posts_active_municipality ON job_posts(is_active, municipality);
CREATE INDEX IF NOT EXISTS idx_job_posts_employment_type ON job_posts(employment_type);
CREATE INDEX IF NOT EXISTS idx_job_posts_salary_max ON job_posts(salary_max);

CREATE INDEX IF NOT EXISTS idx_seeker_profiles_barangay ON job_seeker_profiles(barangay);

-- job_applications(job_post_id) is already indexed by V1.
CREATE INDEX IF NOT EXISTS idx_applications_seeker ON job_applications(job_seeker_id);
