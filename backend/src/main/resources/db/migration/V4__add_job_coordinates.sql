-- Flyway migration V4: optional workplace coordinates and PSGC codes.
--
-- Every column is nullable on purpose: existing job posts keep working with
-- their text-only location, and a job only appears on the map once an employer
-- confirms a real pin. Nothing is backfilled, because inventing coordinates
-- would put a marker at a place no employer chose.

ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS latitude DOUBLE PRECISION;
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS longitude DOUBLE PRECISION;
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS municipality_code VARCHAR(10);
ALTER TABLE job_posts ADD COLUMN IF NOT EXISTS barangay_code VARCHAR(10);

-- A pin is either complete or absent; half a coordinate is a bug, not a place.
ALTER TABLE job_posts DROP CONSTRAINT IF EXISTS job_posts_pin_complete;
ALTER TABLE job_posts ADD CONSTRAINT job_posts_pin_complete CHECK (
    (latitude IS NULL AND longitude IS NULL)
    OR (latitude IS NOT NULL AND longitude IS NOT NULL
        AND latitude BETWEEN -90 AND 90
        AND longitude BETWEEN -180 AND 180)
);

CREATE INDEX IF NOT EXISTS idx_job_posts_barangay_code ON job_posts(barangay_code);
CREATE INDEX IF NOT EXISTS idx_job_posts_municipality_code ON job_posts(municipality_code);
