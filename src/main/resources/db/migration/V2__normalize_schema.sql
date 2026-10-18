-- V2 (16/10/2026): chuẩn hoá DB.
--   1. Bỏ cột không dùng.
--   2. Tỉnh / xã thành khoá ngoại: wards.province_id; accounts, incidents, rescue_stations có province_id + ward_id
--      (tên tỉnh/xã dạng chữ bị bỏ, lấy tên qua khoá ngoại).
--   3. location (PostGIS) tự sinh từ latitude/longitude, có index không gian.
--   4. incident_dispatches: ca <-> trạm (n-n), thay incidents.assigned_station_id.
--   5. incident_media: ảnh / video / ghi âm (file trên R2), thay cột jsonb incidents.media.

-- So tên tỉnh/xã: bỏ dấu, chữ thường, gộp khoảng trắng, bỏ tiền tố "TP." / "Thành phố" / "Tỉnh"
CREATE FUNCTION pg_temp.area_key(t text) RETURNS text LANGUAGE sql STABLE AS $$
    SELECT nullif(regexp_replace(trim(regexp_replace(unaccent(lower(t)), '\s+', ' ', 'g')),
                                 '^(tp\.?|thanh pho|tinh) ', ''), '')
$$;

-- ---------------------------------------------------------------- 1. cột không dùng
ALTER TABLE accounts DROP COLUMN is_dispatcher;
ALTER TABLE incidents DROP COLUMN assigned_dispatcher;
ALTER TABLE incidents DROP COLUMN audio_url;
ALTER TABLE incidents DROP COLUMN transcript;

-- ---------------------------------------------------------------- 2a. wards -> provinces
ALTER TABLE wards RENAME COLUMN province_code TO province_id;
ALTER TABLE wards DROP COLUMN province_name;
ALTER TABLE wards ADD CONSTRAINT fk_wards_province FOREIGN KEY (province_id) REFERENCES provinces (code);
ALTER INDEX IF EXISTS idx_ward_province RENAME TO idx_wards_province;

-- ---------------------------------------------------------------- 3. location tự sinh từ lat/lng
ALTER TABLE incidents DROP COLUMN location;
ALTER TABLE incidents ADD COLUMN location geometry(Point, 4326) GENERATED ALWAYS AS (
    CASE WHEN latitude IS NULL OR longitude IS NULL THEN NULL
         ELSE ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) END) STORED;
CREATE INDEX idx_incidents_location ON incidents USING gist (location);

ALTER TABLE rescue_stations DROP COLUMN location;
ALTER TABLE rescue_stations ADD COLUMN location geometry(Point, 4326) GENERATED ALWAYS AS (
    CASE WHEN latitude IS NULL OR longitude IS NULL THEN NULL
         ELSE ST_SetSRID(ST_MakePoint(longitude, latitude), 4326) END) STORED;
CREATE INDEX idx_rescue_stations_location ON rescue_stations USING gist (location);

CREATE INDEX IF NOT EXISTS idx_wards_geometry ON wards USING gist (geometry);
CREATE INDEX IF NOT EXISTS idx_provinces_geometry ON provinces USING gist (geometry);

-- ---------------------------------------------------------------- 2b. accounts
ALTER TABLE accounts ADD COLUMN province_id varchar(20) REFERENCES provinces (code);
ALTER TABLE accounts ADD COLUMN ward_id bigint REFERENCES wards (id);

UPDATE accounts a SET province_id = p.code
FROM provinces p WHERE pg_temp.area_key(p.name) = pg_temp.area_key(a.province);

UPDATE accounts a SET ward_id = w.id
FROM wards w WHERE w.province_id = a.province_id AND pg_temp.area_key(w.name) = pg_temp.area_key(a.ward);

-- Cấp tỉnh trước đây không lưu xã: lấy xã nơi đặt trụ sở từ địa chỉ ("..., Phường Cái Khế, Thành phố Cần Thơ")
UPDATE accounts a SET ward_id = (
    SELECT w.id FROM wards w
    WHERE w.province_id = a.province_id
      AND position(pg_temp.area_key(w.name) IN pg_temp.area_key(a.address)) > 0
    ORDER BY length(w.name) DESC LIMIT 1)
WHERE a.ward_id IS NULL AND a.province_id IS NOT NULL AND a.address IS NOT NULL;

ALTER TABLE accounts DROP COLUMN province;
ALTER TABLE accounts DROP COLUMN ward;
CREATE INDEX idx_accounts_province ON accounts (province_id);
CREATE INDEX idx_accounts_ward ON accounts (ward_id);

-- ---------------------------------------------------------------- 2c. incidents (theo toạ độ, thiếu thì theo tên)
ALTER TABLE incidents ADD COLUMN province_id varchar(20) REFERENCES provinces (code);
ALTER TABLE incidents ADD COLUMN ward_id bigint REFERENCES wards (id);

UPDATE incidents i SET ward_id = w.id, province_id = w.province_id
FROM wards w WHERE i.location IS NOT NULL AND ST_Contains(w.geometry, i.location);

UPDATE incidents i SET province_id = p.code
FROM provinces p WHERE i.province_id IS NULL AND pg_temp.area_key(p.name) = pg_temp.area_key(i.province);

UPDATE incidents i SET ward_id = w.id
FROM wards w WHERE i.ward_id IS NULL AND w.province_id = i.province_id
  AND pg_temp.area_key(w.name) = pg_temp.area_key(i.ward);

ALTER TABLE incidents DROP COLUMN province;
ALTER TABLE incidents DROP COLUMN ward;
CREATE INDEX idx_incidents_province ON incidents (province_id);
CREATE INDEX idx_incidents_ward ON incidents (ward_id);

-- ---------------------------------------------------------------- 2d. rescue_stations (theo toạ độ, thiếu thì theo tên)
ALTER TABLE rescue_stations ADD COLUMN province_id varchar(20) REFERENCES provinces (code);
ALTER TABLE rescue_stations ADD COLUMN ward_id bigint REFERENCES wards (id);

UPDATE rescue_stations s SET ward_id = w.id, province_id = w.province_id
FROM wards w WHERE s.location IS NOT NULL AND ST_Contains(w.geometry, s.location);

UPDATE rescue_stations s SET province_id = p.code
FROM provinces p WHERE s.province_id IS NULL AND pg_temp.area_key(p.name) = pg_temp.area_key(s.province);

UPDATE rescue_stations s SET ward_id = w.id
FROM wards w WHERE s.ward_id IS NULL AND w.province_id = s.province_id
  AND pg_temp.area_key(w.name) = pg_temp.area_key(s.ward);

ALTER TABLE rescue_stations DROP COLUMN province;
ALTER TABLE rescue_stations DROP COLUMN ward;
CREATE INDEX idx_rescue_stations_province ON rescue_stations (province_id);
CREATE INDEX idx_rescue_stations_ward ON rescue_stations (ward_id);

-- ---------------------------------------------------------------- 4. incident_dispatches (n-n)
CREATE TABLE incident_dispatches (
    incident_id   varchar(50)  NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    station_id    bigint       NOT NULL REFERENCES rescue_stations (id),
    dispatched_by uuid         REFERENCES accounts (account_id),
    unit_level    varchar(20),
    note          varchar(500),
    dispatched_at timestamp(6) NOT NULL,
    PRIMARY KEY (incident_id, station_id)
);
CREATE INDEX idx_incident_dispatches_station ON incident_dispatches (station_id);

INSERT INTO incident_dispatches (incident_id, station_id, dispatched_at)
SELECT i.id, i.assigned_station_id, coalesce(i.updated_at, i.created_at)
FROM incidents i JOIN rescue_stations s ON s.id = i.assigned_station_id;

ALTER TABLE incidents DROP COLUMN assigned_station_id;

-- ---------------------------------------------------------------- 5. incident_media
CREATE TABLE incident_media (
    id                     bigint GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    incident_id            varchar(50)  NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    kind                   varchar(20)  NOT NULL,   -- IMAGE | VIDEO | AUDIO | CALL_RECORDING
    file_name              varchar(100) NOT NULL,   -- tên file trong thư mục của ca (link /incidents/{id}/media/{file_name})
    storage_key            varchar(300) NOT NULL,   -- key trên R2 / đường dẫn local: incidents/{id}/{file_name}
    original_name          varchar(255),
    content_type           varchar(100),
    size_bytes             bigint,
    duration_seconds       integer,                 -- video / ghi âm
    width                  integer,                 -- ảnh / video
    height                 integer,
    sha256                 varchar(64),             -- chứng minh file bằng chứng không bị sửa
    uploaded_by            varchar(20)  NOT NULL,   -- CITIZEN | OFFICER
    uploaded_by_account_id uuid REFERENCES accounts (account_id),
    created_at             timestamp(6) NOT NULL,
    CONSTRAINT ux_incident_media_file UNIQUE (incident_id, file_name)
);

-- jsonb cũ: {"type","fileName","originalName","contentType","size","uploadedAt":[y,m,d,h,mi,s,nanos]}
INSERT INTO incident_media (incident_id, kind, file_name, storage_key, original_name, content_type, size_bytes,
                            uploaded_by, created_at)
SELECT i.id,
       coalesce(m ->> 'type', 'IMAGE'),
       m ->> 'fileName',
       'incidents/' || i.id || '/' || (m ->> 'fileName'),
       m ->> 'originalName',
       m ->> 'contentType',
       (m ->> 'size')::bigint,
       'CITIZEN',
       CASE WHEN jsonb_typeof(m -> 'uploadedAt') = 'array' THEN make_timestamp(
                (m -> 'uploadedAt' ->> 0)::int, (m -> 'uploadedAt' ->> 1)::int, (m -> 'uploadedAt' ->> 2)::int,
                (m -> 'uploadedAt' ->> 3)::int, (m -> 'uploadedAt' ->> 4)::int,
                (m -> 'uploadedAt' ->> 5)::double precision + coalesce((m -> 'uploadedAt' ->> 6)::double precision, 0) / 1e9)
            ELSE i.created_at END
FROM incidents i CROSS JOIN LATERAL jsonb_array_elements(i.media) m
WHERE jsonb_typeof(i.media) = 'array' AND m ->> 'fileName' IS NOT NULL;

ALTER TABLE incidents DROP COLUMN media;
