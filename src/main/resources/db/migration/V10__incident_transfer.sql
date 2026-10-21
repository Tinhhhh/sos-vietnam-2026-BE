-- V10: cán bộ chuyển ca sang lực lượng khác (vd dân chọn Công an nhưng thực tế là cháy -> PCCC).
--   transferred_from: lực lượng trước khi chuyển; transfer_reason: lý do cán bộ ghi; transferred_by / transferred_at
--   Ca chuyển nhiều lần thì giữ lần gần nhất; lịch sử đầy đủ nằm ở incident_claims (end_reason TRANSFERRED + note)
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS transferred_from varchar(20);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS transfer_reason  varchar(500);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS transferred_by   uuid REFERENCES accounts (account_id);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS transferred_at   timestamp(6);
