-- Link cho người phụ trách hiện trường (26/10/2026): mã truy cập riêng của một lượt điều động, DB chỉ lưu SHA-256.
-- Người nhận link không đăng nhập: xem ca + tuyến, gửi GPS từ trình duyệt, gọi video, báo "Đã đến".
-- Mã hết hiệu lực khi ca đóng, khi đổi người phụ trách, hoặc khi cán bộ "Cấp lại mã".
ALTER TABLE incident_dispatches
    ADD COLUMN field_token_hash      varchar(64),
    ADD COLUMN field_token_issued_at timestamp;

CREATE UNIQUE INDEX ux_incident_dispatches_field_token
    ON incident_dispatches (field_token_hash) WHERE field_token_hash IS NOT NULL;
