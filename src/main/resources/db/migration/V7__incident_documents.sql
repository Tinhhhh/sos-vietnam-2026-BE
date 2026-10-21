-- V7: văn bản báo cáo "Phiếu thông báo và tiếp nhận thông tin ứng phó sự cố khẩn cấp" (mẫu bản gốc sos-vietnam-2026).
-- Mỗi ca đã hoàn tất (RESOLVED) có tối đa 1 văn bản. Nội dung sửa được tới khi có đủ chữ ký 2 bên;
-- sửa nội dung sau khi đã có 1 chữ ký thì chữ ký đó bị xoá (ký lại trên nội dung mới).
--   *_sign_type: DRAW (vẽ tay, *_signature = ảnh PNG dạng data URL) / TYPE (gõ họ tên)
--   citizen_sign_channel: CITIZEN (người dân tự ký) / OFFICER_PROXY (cán bộ ký hộ, có lý do + xác nhận được uỷ quyền)
CREATE TABLE incident_documents (
    incident_id           varchar(50)  PRIMARY KEY REFERENCES incidents (id) ON DELETE CASCADE,
    doc_number            varchar(80)  NOT NULL,
    header_upper          varchar(200),           -- cơ quan cấp trên, vd CÔNG AN TP. CẦN THƠ
    header_lower          varchar(200),           -- cơ quan ban hành, vd CÔNG AN PHƯỜNG TÂN AN
    place_date            varchar(200),           -- "Cần Thơ, ngày 21 tháng 10 năm 2026"
    -- A. Đơn vị tiếp nhận
    officer_name          varchar(200),
    officer_title         varchar(200),
    unit_name             varchar(300),
    unit_address          varchar(500),
    unit_phone            varchar(100),
    -- B. Người báo + hiện trường
    reporter_name         varchar(200),
    reporter_phone        varchar(30),
    target_type           varchar(50),
    incident_time         varchar(100),
    incident_address      varchar(500),
    coordinates           varchar(100),
    description           varchar(3000),
    affects_citizens      boolean,
    needs_help            boolean,
    help_request          varchar(2000),
    advice_given          varchar(2000),
    resolution_result     varchar(3000),
    -- Chữ ký cán bộ
    officer_sign_name     varchar(200),
    officer_sign_type     varchar(10),
    officer_signature     text,
    officer_signed_by     uuid REFERENCES accounts (account_id),
    officer_signed_at     timestamp(6),
    -- Chữ ký người dân (tự ký hoặc cán bộ ký hộ)
    citizen_sign_name     varchar(200),
    citizen_sign_type     varchar(10),
    citizen_signature     text,
    citizen_signed_at     timestamp(6),
    citizen_sign_channel  varchar(20),
    citizen_proxy_by      uuid REFERENCES accounts (account_id),
    citizen_proxy_reason  varchar(500),
    created_by            uuid REFERENCES accounts (account_id),
    created_at            timestamp(6) NOT NULL,
    updated_by            uuid REFERENCES accounts (account_id),
    updated_at            timestamp(6) NOT NULL
);

-- Chặn 1 số điện thoại gửi nhiều SOS cùng lúc + tìm lại ca đang mở theo SĐT
CREATE INDEX IF NOT EXISTS ix_incidents_phone_status ON incidents (phone, status);
