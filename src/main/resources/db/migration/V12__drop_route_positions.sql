-- GPS xe sẽ lấy từ Traccar (thiết kế: docs/superpowers/specs/2026-10-24-traccar-tracking-design.md, không lưu vết đường),
-- nên bỏ bảng incident_route_positions (tạo ở V5, chưa từng có code ghi vào).
DROP TABLE IF EXISTS incident_route_positions;
