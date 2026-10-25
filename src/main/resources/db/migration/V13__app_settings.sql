-- Cài đặt chung của hệ thống, sửa được lúc chạy (không cần .env / restart). Hiện có:
-- DEMO_GENERATE (tự sinh ca [DEMO]) và DEMO_HIDDEN (ẩn ca [DEMO] khỏi hàng đợi / lịch sử).
CREATE TABLE app_settings (
    setting_key varchar(50) PRIMARY KEY,
    value       varchar(500) NOT NULL,
    updated_at  timestamp(6) NOT NULL,
    updated_by  uuid REFERENCES unit_members (member_id)
);
