-- Người phụ trách hiện trường của một lượt điều động (25/10/2026): cán bộ đơn vị đi xử lý được chọn,
-- cầm điện thoại GPS (số hiệu = mã thiết bị Traccar). Không cần trực tuyến.
ALTER TABLE incident_dispatches
    ADD COLUMN field_member_id uuid REFERENCES unit_members (member_id);

CREATE INDEX ix_incident_dispatches_field_member
    ON incident_dispatches (field_member_id) WHERE field_member_id IS NOT NULL;

-- Lượt đã xuất phát trước đây: người đi = cán bộ được uỷ quyền, không có thì người điều động (như logic cũ)
UPDATE incident_dispatches
SET field_member_id = COALESCE(assigned_to, dispatched_by)
WHERE status IN ('EN_ROUTE', 'ARRIVED', 'DONE');
