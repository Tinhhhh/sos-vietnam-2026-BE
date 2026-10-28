-- SMS "lực lượng sắp tới" (27/10/2026): gửi 1 lần / lượt khi GPS người đi cách điểm đến <= 50 m.
-- Lưu mốc vào DB để BE khởi động lại không gửi lần nữa.
ALTER TABLE incident_dispatches ADD COLUMN IF NOT EXISTS approach_sms_at timestamp;
