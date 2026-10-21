-- V9: lý do ca cấp xã chuyển lên cấp tỉnh.
--   escalation_reason: NO_RESPONSE_TIMEOUT (quá 15' chưa ai nhận, hệ thống tự chuyển) / CITIZEN_REQUEST (người báo tin
--   yêu cầu khi quá hạn) / OFFICER_REQUEST (cán bộ xã giữ ca chủ động chuyển, kèm lý do)
--   escalation_note: lý do cán bộ ghi (bắt buộc với OFFICER_REQUEST); escalated_by: cán bộ chuyển (null nếu hệ thống / dân)
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS escalation_reason varchar(30);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS escalation_note   varchar(500);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS escalated_by      uuid REFERENCES accounts (account_id);

-- Ca đã leo thang trước V9: lấy theo ghi chú lượt giữ ca ESCALATED nếu có, còn lại coi là quá hạn tự chuyển
UPDATE incidents i
SET escalation_reason = CASE
        WHEN EXISTS (SELECT 1 FROM incident_claims c WHERE c.incident_id = i.id AND c.end_reason = 'ESCALATED'
                     AND c.note LIKE 'Người báo tin%') THEN 'CITIZEN_REQUEST'
        WHEN EXISTS (SELECT 1 FROM incident_claims c WHERE c.incident_id = i.id AND c.end_reason = 'ESCALATED')
            THEN 'OFFICER_REQUEST'
        ELSE 'NO_RESPONSE_TIMEOUT' END
WHERE i.escalated_at IS NOT NULL AND i.escalation_reason IS NULL;
