-- seed_versions "boundaries" trước đây là mốc đồng bộ tên xã + sáp nhập với bando.com.vn.
-- Tách ra: "ward_names" = mốc đồng bộ bando, "boundaries" = ngày bộ file ranh giới (WardDataSeeder ghi lúc khởi động).
UPDATE seed_versions SET name = 'ward_names' WHERE name = 'boundaries';
