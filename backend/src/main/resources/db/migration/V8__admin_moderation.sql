-- Phase 6: admin moderation & platform management.
-- Additive only. No row is deleted and no existing post is unpublished.

-- ---------------------------------------------------------------------------
-- 1. Employer verification state
-- ---------------------------------------------------------------------------
ALTER TABLE employer_profiles
    ADD COLUMN IF NOT EXISTS verification_status VARCHAR(30) NOT NULL DEFAULT 'UNVERIFIED';

-- An employer the platform already verified keeps that decision; nobody is
-- silently moved into the review queue by this migration.
UPDATE employer_profiles p
SET verification_status = 'VERIFIED'
FROM users u
WHERE u.id = p.user_id
  AND u.is_verified = TRUE
  AND p.verification_status <> 'VERIFIED';

-- ---------------------------------------------------------------------------
-- 2. Job moderation state
-- ---------------------------------------------------------------------------
ALTER TABLE job_posts
    ADD COLUMN IF NOT EXISTS moderation_status VARCHAR(30);

-- Every posting that exists today was published under the rules that were in
-- force when it was created, so it is approved rather than pushed back into a
-- review queue it was never part of. This is done once, before the NOT NULL
-- constraint exists, so the backfill can only touch legacy rows.
UPDATE job_posts SET moderation_status = 'APPROVED' WHERE moderation_status IS NULL;

ALTER TABLE job_posts ALTER COLUMN moderation_status SET DEFAULT 'PENDING_REVIEW';
ALTER TABLE job_posts ALTER COLUMN moderation_status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_job_posts_moderation_status
    ON job_posts(moderation_status);

-- ---------------------------------------------------------------------------
-- 3. User reports
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT NOT NULL REFERENCES users(id),
    reported_target_type VARCHAR(30) NOT NULL,
    reported_target_id BIGINT NOT NULL,
    category VARCHAR(40) NOT NULL,
    description TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',
    admin_notes TEXT,
    resolved_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    resolved_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    is_duplicate BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_reports_status ON reports(status);
CREATE INDEX IF NOT EXISTS idx_reports_target ON reports(reported_target_type, reported_target_id);
CREATE INDEX IF NOT EXISTS idx_reports_reporter ON reports(reporter_id);

-- ---------------------------------------------------------------------------
-- 4. Rule-based suspicious listing flags
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS suspicious_listings (
    id BIGSERIAL PRIMARY KEY,
    job_post_id BIGINT NOT NULL REFERENCES job_posts(id) ON DELETE CASCADE,
    flagged_reason TEXT NOT NULL,
    triggered_rule VARCHAR(60) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_REVIEW',
    flagged_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    dismissed_at TIMESTAMP,
    dismiss_reason TEXT,
    flagged_by_admin_id BIGINT REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_suspicious_listings_job ON suspicious_listings(job_post_id);
CREATE INDEX IF NOT EXISTS idx_suspicious_listings_rule ON suspicious_listings(triggered_rule);

-- One live flag per rule per listing: re-running detection cannot duplicate a
-- flag the administrator has not dealt with yet. Dismissed flags are excluded
-- so a new posting revision can be flagged again.
CREATE UNIQUE INDEX IF NOT EXISTS uq_suspicious_listing_open_rule
    ON suspicious_listings(job_post_id, triggered_rule)
    WHERE dismissed = FALSE;

-- ---------------------------------------------------------------------------
-- 5. Immutable administrator audit trail
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS audit_logs (
    id BIGSERIAL PRIMARY KEY,
    action_type VARCHAR(60) NOT NULL,
    admin_user_id BIGINT NOT NULL REFERENCES users(id),
    target_type VARCHAR(40) NOT NULL,
    target_id BIGINT NOT NULL,
    reason TEXT,
    previous_state TEXT,
    new_state TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON audit_logs(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_admin ON audit_logs(admin_user_id);
CREATE INDEX IF NOT EXISTS idx_audit_logs_target ON audit_logs(target_type, target_id);
