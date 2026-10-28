-- Chống báo tin giả (27/10/2026):
-- * incidents.client_ip: IP người gửi SOS (nginx đặt X-Forwarded-For), chỉ để cán bộ thấy cờ cảnh báo, KHÔNG dùng để chặn
--   (4G dùng CGNAT: hàng nghìn thuê bao chung 1 IP).
-- * phone_locks: mỗi lần khoá 1 SĐT. locked_until null = khoá vĩnh viễn (lần khoá thứ 3 trong 30 ngày), chỉ ADMIN gỡ.
--   lifted_at = ADMIN gỡ tay (hết hạn tự nhiên thì để trống).
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS client_ip varchar(45);
CREATE INDEX IF NOT EXISTS idx_incidents_client_ip ON incidents (client_ip, created_at) WHERE client_ip IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_incidents_phone_cancel ON incidents (phone, cancelled_at) WHERE cancel_reason IS NOT NULL;

CREATE TABLE IF NOT EXISTS phone_locks (
    id            bigserial PRIMARY KEY,
    phone         varchar(10)  NOT NULL,
    reason        varchar(20)  NOT NULL,
    locked_at     timestamp    NOT NULL,
    locked_until  timestamp,
    permanent     boolean      NOT NULL DEFAULT false,
    lifted_at     timestamp,
    lifted_by     uuid REFERENCES unit_members (member_id),
    lift_note     varchar(255)
);
CREATE INDEX IF NOT EXISTS idx_phone_locks_phone ON phone_locks (phone, locked_at DESC);
