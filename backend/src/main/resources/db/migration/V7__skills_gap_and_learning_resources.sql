-- Skills gap analysis: admin-managed learning resources and private seeker
-- learning interests. Additive only: no existing table or row is changed.
--
-- Skills themselves are not duplicated here. They stay in
-- job_seeker_profiles.skills and job_posts.required_skills, which the matching
-- engine already owns and normalizes.

CREATE TABLE IF NOT EXISTS learning_resources (
    id BIGSERIAL PRIMARY KEY,
    skill_name VARCHAR(150) NOT NULL,
    skill_key VARCHAR(150) NOT NULL,
    title VARCHAR(250) NOT NULL,
    provider VARCHAR(200) NOT NULL,
    url VARCHAR(500) NOT NULL,
    cost NUMERIC(10, 2),
    is_free BOOLEAN NOT NULL DEFAULT FALSE,
    is_online BOOLEAN NOT NULL DEFAULT TRUE,
    duration VARCHAR(120),
    description TEXT,
    availability VARCHAR(200),
    verified_at DATE,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- A seeker's private list of skills they want to learn. It never feeds their
-- profile until they add the skill themselves.
CREATE TABLE IF NOT EXISTS skill_learning_interests (
    id BIGSERIAL PRIMARY KEY,
    seeker_profile_id BIGINT NOT NULL REFERENCES job_seeker_profiles(id) ON DELETE CASCADE,
    skill_name VARCHAR(150) NOT NULL,
    skill_key VARCHAR(150) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_learning_interest UNIQUE (seeker_profile_id, skill_key)
);

CREATE INDEX IF NOT EXISTS idx_learning_resources_skill
    ON learning_resources(skill_key);
CREATE INDEX IF NOT EXISTS idx_learning_resources_active
    ON learning_resources(is_active);
CREATE INDEX IF NOT EXISTS idx_learning_interests_seeker
    ON skill_learning_interests(seeker_profile_id);
