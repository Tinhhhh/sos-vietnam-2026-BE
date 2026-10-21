-- V5: module điều động (đã chốt 19/10/2026)
--   1. incident_dispatches: khoá riêng `id` (1 trạm có thể được chọn lại sau khi từ chối) + vòng đời lượt điều động
--   2. incident_routes: tuyến đường của một lượt điều động (điểm đi = trạm, điểm đến = hiện trường), tính lại thì
--      đánh dấu superseded_at cho dòng cũ, thêm dòng mới
--   3. incident_route_positions: vị trí GPS xe đang chạy (máy cán bộ được giao gửi lên)
--   4. incidents: điểm đến hiện hành (cán bộ sửa khi GPS dân lệch) + hai bên xác nhận hoàn tất

-- ---------------------------------------------------------------- 1. incident_dispatches
ALTER TABLE incident_dispatches DROP CONSTRAINT incident_dispatches_pkey;
ALTER TABLE incident_dispatches ADD COLUMN id bigserial PRIMARY KEY;
ALTER TABLE incident_dispatches
    -- PRIMARY = đơn vị chủ trì (mỗi ca 1 lượt đang hoạt động), SUPPORT = hỗ trợ
    ADD COLUMN role          varchar(20) NOT NULL DEFAULT 'PRIMARY',
    -- PENDING_ACCEPT (chờ đơn vị được uỷ quyền nhận) / ACCEPTED / REJECTED / EN_ROUTE / ARRIVED / DONE
    ADD COLUMN status        varchar(20) NOT NULL DEFAULT 'EN_ROUTE',
    ADD COLUMN assigned_to   uuid REFERENCES accounts (account_id), -- cán bộ đơn vị được uỷ quyền
    ADD COLUMN responded_at  timestamp(6),
    ADD COLUMN reject_reason varchar(255),
    ADD COLUMN departed_at   timestamp(6),
    ADD COLUMN arrived_at    timestamp(6),
    ADD COLUMN updated_at    timestamp(6);
-- Dòng cũ (do /dispatch ghi) là lực lượng hỗ trợ
UPDATE incident_dispatches SET role = 'SUPPORT';
CREATE INDEX idx_incident_dispatches_incident ON incident_dispatches (incident_id);
CREATE UNIQUE INDEX ux_incident_dispatches_primary ON incident_dispatches (incident_id)
    WHERE role = 'PRIMARY' AND status <> 'REJECTED';

-- ---------------------------------------------------------------- 2. incident_routes
CREATE TABLE incident_routes (
    id                 bigserial PRIMARY KEY,
    dispatch_id        bigint           NOT NULL REFERENCES incident_dispatches (id) ON DELETE CASCADE,
    incident_id        varchar(50)      NOT NULL REFERENCES incidents (id) ON DELETE CASCADE,
    station_id         bigint REFERENCES rescue_stations (id),
    origin_lat         double precision NOT NULL,
    origin_lng         double precision NOT NULL,
    origin_address     varchar(500),
    origin_source      varchar(20)      NOT NULL, -- STATION / CUSTOM
    dest_lat           double precision NOT NULL,
    dest_lng           double precision NOT NULL,
    dest_address       varchar(500),
    dest_source        varchar(20)      NOT NULL, -- CITIZEN_GPS / OFFICER_EDIT
    distance_m         integer,
    duration_s         integer,
    duration_traffic_s integer,                   -- theo tắc đường (Google), null nếu không có
    polyline           text,                      -- encoded polyline (định dạng Google, độ chính xác 1e-5)
    provider           varchar(20),               -- GOOGLE / OSRM / ESTIMATE
    computed_by        uuid REFERENCES accounts (account_id),
    computed_at        timestamp(6)     NOT NULL,
    superseded_at      timestamp(6)               -- null = tuyến đang dùng
);
CREATE INDEX idx_incident_routes_dispatch ON incident_routes (dispatch_id) WHERE superseded_at IS NULL;
CREATE INDEX idx_incident_routes_incident ON incident_routes (incident_id);

-- ---------------------------------------------------------------- 3. incident_route_positions
CREATE TABLE incident_route_positions (
    id          bigserial PRIMARY KEY,
    dispatch_id bigint           NOT NULL REFERENCES incident_dispatches (id) ON DELETE CASCADE,
    account_id  uuid REFERENCES accounts (account_id),
    latitude    double precision NOT NULL,
    longitude   double precision NOT NULL,
    accuracy    double precision,
    heading     double precision,
    speed       double precision,
    recorded_at timestamp(6)     NOT NULL
);
CREATE INDEX idx_incident_route_positions_dispatch ON incident_route_positions (dispatch_id, recorded_at);

-- ---------------------------------------------------------------- 4. incidents
ALTER TABLE incidents
    ADD COLUMN dest_latitude        double precision,
    ADD COLUMN dest_longitude       double precision,
    ADD COLUMN dest_address         varchar(500),
    ADD COLUMN dest_source          varchar(20),
    ADD COLUMN dest_updated_by      uuid REFERENCES accounts (account_id),
    ADD COLUMN officer_confirmed_at timestamp(6),
    ADD COLUMN officer_confirmed_by uuid REFERENCES accounts (account_id),
    ADD COLUMN citizen_confirmed_at timestamp(6);
