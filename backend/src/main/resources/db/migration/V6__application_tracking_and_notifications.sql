-- Application tracking, interviews and persistent notifications.
-- Additive only: no row is deleted, and the two lifecycle values are renamed in
-- place so existing applications keep their state.

-- 1. Lifecycle names now match the product language.
UPDATE job_applications SET application_status = 'SUBMITTED' WHERE application_status = 'APPLIED';
UPDATE job_applications SET application_status = 'REVIEWING' WHERE application_status = 'UNDER_REVIEW';

ALTER TABLE job_applications ALTER COLUMN application_status SET DEFAULT 'SUBMITTED';
ALTER TABLE job_applications ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP;

-- 2. Immutable history. The application service is the only writer; there is no
-- update or delete endpoint for these rows.
CREATE TABLE IF NOT EXISTS application_status_history (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL REFERENCES job_applications(id) ON DELETE CASCADE,
    previous_status VARCHAR(50),
    new_status VARCHAR(50) NOT NULL,
    event_type VARCHAR(40) NOT NULL,
    actor_user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    actor_role VARCHAR(20),
    actor_name VARCHAR(200),
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Every existing application really was submitted on its applied_at date, so
-- that one genuine event is backfilled. Later statuses are not invented; the
-- timeline reports them as the current status without a timestamp.
INSERT INTO application_status_history
    (application_id, previous_status, new_status, event_type, actor_user_id, actor_role, actor_name, created_at)
SELECT a.id,
       NULL,
       'SUBMITTED',
       'SUBMITTED',
       s.user_id,
       'JOB_SEEKER',
       s.full_name,
       COALESCE(a.applied_at, CURRENT_TIMESTAMP)
FROM job_applications a
JOIN job_seeker_profiles s ON s.id = a.job_seeker_id
WHERE NOT EXISTS (
    SELECT 1 FROM application_status_history h WHERE h.application_id = a.id
);

-- 3. Interview arrangement, one row per application; reschedules keep the old
-- appointment in the history above.
CREATE TABLE IF NOT EXISTS application_interviews (
    id BIGSERIAL PRIMARY KEY,
    application_id BIGINT NOT NULL UNIQUE REFERENCES job_applications(id) ON DELETE CASCADE,
    interview_at TIMESTAMP NOT NULL,
    interview_type VARCHAR(20) NOT NULL,
    location VARCHAR(300),
    meeting_link VARCHAR(500),
    instructions TEXT,
    employer_message TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'SCHEDULED',
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 4. Notifications gain type, destination, reference and read time. The old
-- columns stay, so existing notifications keep working.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS title VARCHAR(200);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS type VARCHAR(60);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS link VARCHAR(300);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_type VARCHAR(40);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_id BIGINT;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS read_at TIMESTAMP;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(200);

-- A notification is created once per recipient per event. PostgreSQL treats
-- NULL dedupe keys as distinct, so old rows without a key are unaffected.
CREATE UNIQUE INDEX IF NOT EXISTS uq_notifications_user_dedupe
    ON notifications(user_id, dedupe_key);

-- 5. Indexes for the screens this feature adds.
CREATE INDEX IF NOT EXISTS idx_app_history_application
    ON application_status_history(application_id);
CREATE INDEX IF NOT EXISTS idx_app_history_created
    ON application_status_history(created_at);
CREATE INDEX IF NOT EXISTS idx_applications_seeker
    ON job_applications(job_seeker_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications(user_id, is_read);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created
    ON notifications(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_interviews_application
    ON application_interviews(application_id);
