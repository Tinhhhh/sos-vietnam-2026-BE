-- Người phụ trách hiện trường chọn ngay lúc "Yêu cầu điều động" (25/10/2026): lưu trên ca để bước chọn trạm dùng lại
-- khi đơn vị của người giữ ca tự đi. Lượt uỷ quyền vẫn do cán bộ xã được giao tự chọn lúc xuất phát.
ALTER TABLE incidents
    ADD COLUMN field_member_id uuid REFERENCES unit_members (member_id);
