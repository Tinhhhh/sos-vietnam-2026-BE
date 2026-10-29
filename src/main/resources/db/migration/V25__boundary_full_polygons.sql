-- Ranh giới gốc (chưa rút gọn) của tỉnh / xã, BandoSyncService tải từ sapnhap.bando.com.vn.
-- Cột geometry giữ bản rút gọn từ geo/*.geojson (entity Ward / Province map cột đó, nạp nhẹ);
-- boundary không map vào entity, chỉ dùng trong SQL tìm điểm thuộc xã / tỉnh nào. NULL = chưa đồng bộ, dùng geometry.
ALTER TABLE wards ADD COLUMN boundary geometry(Geometry, 4326);
ALTER TABLE provinces ADD COLUMN boundary geometry(Geometry, 4326);
CREATE INDEX idx_wards_boundary ON wards USING gist (boundary);
CREATE INDEX idx_provinces_boundary ON provinces USING gist (boundary);
