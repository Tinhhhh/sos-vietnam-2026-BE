-- Loại báo tin: SELF = người gặp sự cố tự báo, PROXY = báo hộ người khác (chưa mở trên giao diện).
-- Mọi ca cũ đều do chính người báo gửi -> SELF.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS report_type varchar(10);
UPDATE incidents SET report_type = 'SELF' WHERE report_type IS NULL;
ALTER TABLE incidents ALTER COLUMN report_type SET DEFAULT 'SELF';
ALTER TABLE incidents ALTER COLUMN report_type SET NOT NULL;
