-- "Đã đến" theo GPS (27/10/2026): chọn người phụ trách xong vẫn ACCEPTED, bấm "Khởi hành" mới EN_ROUTE.
-- Đã đến hợp lệ khi điểm GPS mới nhất cách điểm đến <= 30 m + sai số (tối đa 50 m); ngoại lệ: người giữ ca
-- xác nhận kèm lý do (không có GPS / điện thoại hỏng), hoặc người báo tin xác nhận.
ALTER TABLE incident_dispatches
    ADD COLUMN arrival_source      varchar(20),
    ADD COLUMN arrival_note        varchar(255),
    ADD COLUMN arrival_by_member_id uuid REFERENCES unit_members (member_id),
    ADD COLUMN arrival_distance_m  integer;
