-- Phase 8: persistent, database-backed rate limiting.
-- Additive only: one new table, nothing existing is touched.

CREATE TABLE IF NOT EXISTS rate_limit_counters (
    bucket_key    VARCHAR(200) PRIMARY KEY,
    window_start  BIGINT       NOT NULL,
    hits          INTEGER      NOT NULL DEFAULT 0,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Cleanup of stale windows scans by age.
CREATE INDEX IF NOT EXISTS idx_rate_limit_updated ON rate_limit_counters(updated_at);
