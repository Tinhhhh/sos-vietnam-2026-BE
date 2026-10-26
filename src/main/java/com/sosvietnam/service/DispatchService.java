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

    /**
     * "Yêu cầu điều động": người giữ ca chọn người phụ trách hiện trường của đơn vị mình (cùng điều kiện như lúc
     * chọn ở bước điều động). Sai -> 400 / 403 / 409.
     */
    void checkFieldMember(String incidentId, UUID fieldMemberId);

    /** Đổi người phụ trách hiện trường khi lực lượng đang trên đường (cùng người được chọn như trên). */
    DispatchPlanResponse changeFieldMember(String incidentId, UUID fieldMemberId);

    /**
     * "Đã đến" ở bước Theo dõi: lực lượng tới hiện trường (EN_ROUTE -> ARRIVED, bước 4). Một bên bấm là đủ:
     * cán bộ được giao (đơn vị được uỷ quyền), người giữ ca khi chính đơn vị mình đi, người phụ trách hiện trường,
     * hoặc người dân (citizenAccessToken).
     */
    DispatchPlanResponse arrive(String incidentId, String citizenAccessToken);

    /**
     * "Mô phỏng": giả lập điện thoại người đi hiện trường chạy dọc tuyến tới gần hiện trường (mọi ca, 25/10/2026).
     * Cán bộ xem được ca; lượt phải EN_ROUTE và người đi phải có số hiệu (= mã thiết bị). Không đổi trạng thái lượt.
     */
    DispatchPlanResponse startSimulation(String incidentId);

    DispatchPlanResponse stopSimulation(String incidentId);

    /**
     * CHỈ ĐỂ DEMO "Rẽ đường khác": xe giả rẽ phải khỏi tuyến từ vị trí hiện tại (hoặc trạm), đi vòng một đoạn rồi
     * mới tới hiện trường, để thử BE tự tính lại tuyến khi xe lệch đường. Cùng điều kiện như startSimulation.
     */
    DispatchPlanResponse detourSimulation(String incidentId);

    /**
     * Bước 4 "Đã xử lý xong" (lực lượng đã ở hiện trường): ghi xác nhận của người dân (citizenAccessToken) hoặc cán bộ
     * (JWT). Thứ tự nào cũng được; cán bộ đã lập văn bản "Đã xử lý xong" (finish) rồi thì xác nhận này đóng ca.
     */
    DispatchPlanResponse confirm(String incidentId, String citizenAccessToken);

    /**
     * Văn bản kết thúc (người giữ ca hoặc cán bộ được giao). RESOLVED: ghi văn bản + xác nhận của cán bộ, đóng ca khi
     * dân cũng đã xác nhận. FALSE_ALARM / TEST: đóng ngay. force: chỉ người giữ ca, khi lực lượng đã điều động.
     * @return true nếu ca đã đóng (RESOLVED), false nếu đang chờ người dân xác nhận
     */
    boolean finish(String incidentId, FinishIncidentRequest request);
}
