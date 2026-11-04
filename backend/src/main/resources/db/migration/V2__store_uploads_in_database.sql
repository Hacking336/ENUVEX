-- Flyway migration V2: keep uploaded images in PostgreSQL.
--
-- Render's free web services have an ephemeral filesystem: anything written to
-- disk is lost on every redeploy, restart or spin-down. Profile photos and
-- employer logos used to live in ./uploads, so they disappeared while the
-- profile row kept pointing at a file that no longer existed.
--
-- Files are small (multipart uploads are capped at 5MB) and read by filename,
-- so a natural primary key on the filename is enough; filename lookups use the
-- primary key index and create_at needs no index of its own.
CREATE TABLE IF NOT EXISTS uploaded_files (
    filename     VARCHAR(255) PRIMARY KEY,
    content_type VARCHAR(120),
    size_bytes   INTEGER NOT NULL,
    data         BYTEA NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
