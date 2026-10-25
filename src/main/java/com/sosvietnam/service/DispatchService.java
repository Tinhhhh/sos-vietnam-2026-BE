package com.sosvietnam.service;

import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import java.util.List;
import java.util.UUID;
import com.sosvietnam.model.payload.request.DispatchPlanRequest;
import com.sosvietnam.model.payload.request.FinishIncidentRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.model.payload.response.FieldCandidateResponse;

/** Kế hoạch điều động của một ca: trạm xuất phát, điểm đến, tuyến đường, bước hiện tại. */
public interface DispatchService {

    /**
     * Kế hoạch hiện hành, null nếu ca chưa chọn trạm xuất phát.
     * Cán bộ xem được ca (JWT, cùng phạm vi hàng đợi) hoặc người báo tin (citizenAccessToken).
     */
    DispatchPlanResponse getPlan(String incidentId, String citizenAccessToken);

    /**
     * Bước 1 "Điều động" của cán bộ đang giữ ca (ca RESOLVING): chọn / đổi trạm xuất phát, điểm đến, tuyến.
     * Tuyến cũ được giữ lại (superseded_at), điểm đến hiện hành ghi vào incidents.dest_*.
     */
    DispatchPlanResponse savePlan(String incidentId, DispatchPlanRequest request);

    /** Bước 2: cán bộ của đơn vị sở hữu trạm đã chọn (cấp xã), kèm trực tuyến / đang giữ ca. Chỉ người giữ ca. */
    List<OfficerStatusResponse> getCandidates(String incidentId);

    /** Bước 2 "Lập tức liên hệ": giao lượt cho một cán bộ của đơn vị đó; ca vào hàng đợi của đơn vị, chờ nhận. */
    DispatchPlanResponse delegate(String incidentId, UUID memberId);

    /**
     * Bên giao thu hồi lượt đã gửi mà người kia chưa trả lời (không nhận / không liên lạc được):
     * bỏ người được giao, kế hoạch quay về bước 2 chưa liên hệ ai (mở khoá: đổi người, đổi trạm, quay lại).
     */
    DispatchPlanResponse withdraw(String incidentId);

    /** Cán bộ được giao chấp nhận (PENDING_ACCEPT -> ACCEPTED). */
    DispatchPlanResponse accept(String incidentId);

    /** Cán bộ được giao từ chối kèm lý do (-> REJECTED); bên giao chọn lại đơn vị khác. */
    DispatchPlanResponse reject(String incidentId, String reason);

    /**
     * "Lập tức điều động" của đơn vị được giao (ACCEPTED -> EN_ROUTE), kèm người phụ trách hiện trường
     * (cán bộ đơn vị mình, cầm điện thoại GPS): người dân bắt đầu theo dõi được.
     */
    DispatchPlanResponse depart(String incidentId, UUID fieldMemberId);

    /**
     * Cán bộ đơn vị đi xử lý để chọn người phụ trách hiện trường (không cần trực tuyến).
     * Chỉ người nhận ca của đơn vị đi: cán bộ được uỷ quyền, không có thì người giữ ca.
     */
    List<FieldCandidateResponse> getFieldCandidates(String incidentId);

    /** Đổi người phụ trách hiện trường khi lực lượng đang trên đường (cùng người được chọn như trên). */
    DispatchPlanResponse changeFieldMember(String incidentId, UUID fieldMemberId);

    /**
     * "Khởi hành ngay" ở bước Theo dõi: lực lượng tới hiện trường (EN_ROUTE -> ARRIVED, bước 4 Kết thúc).
     * Cán bộ được giao (đơn vị được uỷ quyền), hoặc người giữ ca khi chính đơn vị mình đi.
     */
    DispatchPlanResponse arrive(String incidentId);

    /**
     * CHỈ ĐỂ DEMO ("Mô phỏng di chuyển"): giả lập điện thoại người đi hiện trường chạy dọc tuyến tới gần hiện trường.
     * Cán bộ xem được ca; chỉ khi công tắc ca demo đang bật hoặc ca là ca [DEMO]; lượt phải EN_ROUTE và người đi
     * phải có số hiệu (= mã thiết bị). Không đổi trạng thái lượt.
     */
    DispatchPlanResponse startSimulation(String incidentId);

    DispatchPlanResponse stopSimulation(String incidentId);

    /**
     * Bước 4 "Xác nhận đã xử lý xong" (lực lượng đã ở hiện trường): đơn vị đi xử lý (cán bộ được giao, hoặc
     * người giữ ca khi chính đơn vị mình đi) dùng JWT; người dân dùng citizenAccessToken.
     */
    DispatchPlanResponse confirm(String incidentId, String citizenAccessToken);

    /**
     * Văn bản kết thúc, ca -> RESOLVED. Bình thường: cả hai bên đã xác nhận, người giữ ca hoặc cán bộ được giao lập.
     * force: chỉ người giữ ca, khi lực lượng đã điều động (EN_ROUTE / ARRIVED), không chờ được xác nhận.
     */
    void finish(String incidentId, FinishIncidentRequest request);
}
