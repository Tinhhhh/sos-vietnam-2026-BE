-- Huỷ ca trước đây không đóng lượt điều động: lượt của ca đã huỷ kẹt ở EN_ROUTE (GPS, mô phỏng vẫn tìm thấy nó).
-- Đóng như lúc huỷ ca bây giờ: REJECTED + lý do, link người đi hiện trường hết hiệu lực.
UPDATE incident_dispatches d
SET status = 'REJECTED',
    reject_reason = COALESCE(d.reject_reason, 'Ca đã bị huỷ'),
    field_token_hash = NULL,
    field_token_issued_at = NULL,
    updated_at = NOW()
FROM incidents i
WHERE i.id = d.incident_id
  AND i.status = 'CANCELLED'
  AND d.status IN ('PENDING_ACCEPT', 'ACCEPTED', 'EN_ROUTE', 'ARRIVED');
