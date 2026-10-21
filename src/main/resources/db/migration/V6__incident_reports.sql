-- V6: văn bản kết thúc ca (bước 4 điều động). Mỗi ca đóng bằng biên bản có đúng 1 dòng.
--   result: RESOLVED (đã xử lý xong) / FALSE_ALARM (báo khống) / FORCED (cán bộ kết thúc bắt buộc)
CREATE TABLE incident_reports (
    incident_id varchar(50)   PRIMARY KEY REFERENCES incidents (id) ON DELETE CASCADE,
    result      varchar(20)   NOT NULL,
    summary     varchar(2000),          -- diễn biến / kết quả xử lý
    forces      varchar(500),           -- lực lượng, phương tiện tham gia
    note        varchar(1000),          -- ghi chú, lý do kết thúc bắt buộc
    created_by  uuid REFERENCES accounts (account_id),
    created_at  timestamp(6)  NOT NULL
);
