CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE IF EXISTS rescue_stations ALTER COLUMN phone TYPE VARCHAR(100);
-- Checksum of each seed file already loaded (DataSeeder skips a file whose checksum is unchanged)
CREATE TABLE IF NOT EXISTS seed_versions (
    name VARCHAR(100) PRIMARY KEY,
    checksum VARCHAR(64) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- Shown by GET /api/v1/geo/data-status: version label and last time the source was checked
ALTER TABLE seed_versions ADD COLUMN IF NOT EXISTS version VARCHAR(50);
ALTER TABLE seed_versions ADD COLUMN IF NOT EXISTS checked_at TIMESTAMPTZ;
-- Enum columns are validated in Java. Hibernate adds a CHECK with the enum values when it
-- creates a column but never updates it, so a new enum constant (e.g. MOBILE_POLICE) would be rejected.
ALTER TABLE IF EXISTS accounts DROP CONSTRAINT IF EXISTS accounts_agency_type_check;
ALTER TABLE IF EXISTS rescue_stations DROP CONSTRAINT IF EXISTS rescue_stations_agency_type_check;
