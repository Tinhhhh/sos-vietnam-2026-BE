-- Trạm tự sinh (điền cho đủ mỗi xã một trạm Công an, địa chỉ / SĐT chưa có, toạ độ ước) khác trạm thật (có nguồn công bố
-- hoặc nhập tay). DataSeeder ghi cột này từ field "auto_generated" trong data/rescue_stations.json.
ALTER TABLE rescue_stations ADD COLUMN auto_generated boolean NOT NULL DEFAULT false;
