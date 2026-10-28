-- SMS "đã tiếp nhận" (27/10/2026, docs/spec-multi-case.md): gửi 1 lần / ca lúc cán bộ nhận ca lần đầu, kèm mã ca để
-- dân Tra cứu vào lại màn hình gọi. null = chưa gửi. Cũng dùng để đếm giới hạn 20 tin / số / 24 giờ.
ALTER TABLE incidents ADD COLUMN received_sms_at timestamp;

CREATE INDEX idx_incidents_phone_received_sms ON incidents (phone, received_sms_at) WHERE received_sms_at IS NOT NULL;
