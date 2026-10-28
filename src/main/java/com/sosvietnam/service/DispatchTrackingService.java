package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;

/**
 * Vị trí xe / cán bộ đang đi hiện trường, nhận từ Traccar (app Traccar Client, mã thiết bị = số hiệu cán bộ).
 * Chỉ giữ điểm mới nhất của mỗi lượt điều động trong RAM (không lưu DB, chỉ đúng khi chạy 1 instance BE).
 * Không bao giờ đổi trạng thái lượt: tới nơi vẫn do người bấm xác nhận.
 */
public interface DispatchTrackingService {

    /**
     * Nhận một điểm. Gắn vào lượt EN_ROUTE có người đi mang số hiệu đó; thiết bị không thuộc lượt nào đang chạy,
     * hoặc toạ độ sai thì bỏ qua.
     * @return true nếu điểm được dùng
     */
    boolean accept(TraccarPositionRequest request);

    /**
     * Điểm của xe giả ("Mô phỏng") cho lượt này, nạp thẳng không qua Traccar. Trong lúc mô phỏng chạy, điểm thật
     * (Traccar, trang link) của lượt đó bị bỏ qua: điện thoại người đi đang đứng gần hiện trường sẽ không kéo xe về đó.
     * @return true nếu điểm được dùng
     */
    boolean acceptSimulated(Long dispatchId, double lat, double lng, double speedKmh, double heading);

    /** Điểm mới nhất của lượt điều động, null nếu chưa có. */
    DispatchPlanResponse.LivePosition lastPosition(Long dispatchId);

    /**
     * Điểm GPS từ trình duyệt của người phụ trách hiện trường (trang link). App Traccar chính xác hơn nên được ưu tiên:
     * app đã gửi điểm trong 20 giây gần nhất thì bỏ điểm trình duyệt (trả false).
     */
    boolean acceptBrowser(Long dispatchId, com.sosvietnam.model.payload.request.FieldPositionRequest point);
}
