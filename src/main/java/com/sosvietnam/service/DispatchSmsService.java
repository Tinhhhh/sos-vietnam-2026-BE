package com.sosvietnam.service;

/**
 * SMS theo lượt điều động (chạy nền, cổng SMS chưa cấu hình = bỏ qua, không gửi cho ca demo):
 * - người báo tin: lần đầu GPS người đi hiện trường cách điểm đến <= {@code app.sms.approach-distance-m} (50 m):
 *   "lực lượng sắp tới, chú ý điện thoại / xác nhận trên màn theo dõi"; 1 lần / lượt
 *   ({@code incident_dispatches.approach_sms_at}).
 * - người báo tin: lúc lượt "Khởi hành" (EN_ROUTE): "lực lượng đã khởi hành", giờ tới dự kiến + tên người đi; 1 lần / lượt.
 * - lực lượng đã đi (người đi hiện trường + cán bộ được uỷ quyền nếu khác người): khi ca đóng, "ca đã kết thúc".
 */
public interface DispatchSmsService {

    /** Lượt vừa "Khởi hành" (gọi sau commit). */
    void onDeparted(String incidentId, Long dispatchId);

    /** Mỗi điểm GPS mới của lượt đang đi (app Traccar hoặc trang link). */
    void onPosition(Long dispatchId, double lat, double lng);

    /** Ca vừa đóng (RESOLVED: xử lý xong, báo khống, ca test, kết thúc bắt buộc). */
    void onClosed(String incidentId, Long dispatchId, String resultLabel);
}
