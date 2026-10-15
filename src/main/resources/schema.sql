CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE IF EXISTS rescue_stations ALTER COLUMN phone TYPE VARCHAR(100);
-- Checksum of each seed file already loaded (DataSeeder skips a file whose checksum is unchanged)
CREATE TABLE IF NOT EXISTS seed_versions (
    name VARCHAR(100) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
