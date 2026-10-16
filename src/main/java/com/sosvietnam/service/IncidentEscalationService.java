package com.sosvietnam.service;

import com.sosvietnam.model.entity.Incident;

/**
 * Leo thang ca Công an cấp xã lên cấp tỉnh khi quá hạn (mặc định 15 phút) mà chưa ai nhận.
 * Mỗi ca được hẹn giờ đúng lúc escalate_at; khi BE khởi động thì hẹn lại từ DB (hẹn giờ trong bộ nhớ
 * mất khi restart, ca quá hạn trong lúc BE tắt sẽ leo thang ngay khi bật lại).
 */
public interface IncidentEscalationService {

    /** Hẹn giờ leo thang cho ca (không làm gì nếu ca không leo thang). Gọi khi tạo ca và khi ca bị nhả về hàng đợi. */
    void schedule(Incident incident);

    /** Leo thang nếu ca vẫn chưa ai nhận và đã quá hạn. Trả về true nếu đã leo thang. */
    boolean escalateIfDue(String incidentId);
}
