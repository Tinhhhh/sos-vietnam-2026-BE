package com.sosvietnam.service;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.ReleaseIncidentRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.HandoverCandidateResponse;
import com.sosvietnam.model.payload.response.IncidentDetailResponse;
import com.sosvietnam.model.payload.response.IncidentLookupResponse;
import com.sosvietnam.model.payload.response.IncidentQueueResponse;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IncidentService {
    CreateIncidentResponse createIncident(CreateIncidentRequest request);

    IncidentResponse dispatchIncident(String id, DispatchRequest request);

    /** Hàng đợi của đơn vị người đang đăng nhập (ca chưa xong, đúng lực lượng + địa bàn), mới nhất trước, phân trang. */
    /** @param days chỉ ca báo trong N ngày gần nhất (1-90), null = mọi ca đang mở */
    ListResponse getQueue(int pageNo, int pageSize, Integer days);

    /**
     * Ca đã đóng (RESOLVED / CANCELLED) mà cán bộ được xem, mới nhất trước.
     * @param days 1-90, null = 7
     * @param query tìm theo mã ca, tên / SĐT người báo, địa chỉ, xã (không dấu, không phân biệt hoa thường)
     * @param agency chỉ ADMIN được lọc theo lực lượng; cán bộ luôn là lực lượng của mình
     */
    ListResponse getHistory(Integer days, String query, AgencyType agency, int pageNo, int pageSize);

    /** Chi tiết 1 ca cho cán bộ (cùng phạm vi xem với hàng đợi / lịch sử): media, điều động, lượt giữ ca. */
    IncidentDetailResponse getDetail(String id);

    /** Phân luồng bù các ca còn mở chưa có lực lượng / cấp (do BE bản cũ tạo). Trả về số ca đã xử lý. */
    int routeUnroutedIncidents();

    /** Nhận ca: khoá ca cho người đang đăng nhập (mỗi trực ban chỉ giữ 1 ca). */
    IncidentQueueResponse claimIncident(String id);

    /** Nhả ca về hàng đợi chung, hoặc giao thẳng cho trực ban cùng đơn vị (request.to_account_id). */
    IncidentQueueResponse releaseIncident(String id, ReleaseIncidentRequest request);

    /** Trực ban cùng đơn vị có thể nhận bàn giao ca này. */
    List<HandoverCandidateResponse> getHandoverCandidates(String id);

    List<IncidentResponse.MediaResponse> addMedia(String id, String citizenAccessToken, List<MultipartFile> files);

    MediaAccess loadMedia(String id, String fileName);

    /** Người báo tin xem trạng thái ca của mình (citizen access token). */
    IncidentTrackingResponse getTracking(String id, String citizenAccessToken);

    /** Người dân tra cứu lại ca bằng mã tra cứu + 4 số cuối SĐT đã báo. */
    IncidentLookupResponse lookup(IncidentLookupRequest request);


    /** Cán bộ đang giữ ca huỷ ca ở bất kỳ bước nào (xác minh, điều động), kèm lý do. */
    void cancelByOfficer(String id, String reason);

    /** Người báo tin tự huỷ ca còn mở (kể cả khi đã có cán bộ nhận). */
    IncidentTrackingResponse cancelByCitizen(String id, String citizenAccessToken, CancelIncidentRequest request);

    /** Cán bộ giữ ca xác minh xong, gửi yêu cầu điều động lực lượng (VERIFYING -> RESOLVING). */
    IncidentQueueResponse requestDispatch(String id);

    /**
     * Chuyển ca cấp xã lên cấp tỉnh ngay. Cán bộ đang giữ ca: lúc VERIFYING hoặc RESOLVING (không xử lý được).
     * Người dân (citizen token): chỉ khi ca vẫn VERIFYING và đã quá hạn 15 phút (cán bộ nhận rồi treo máy).
     */
    IncidentTrackingResponse escalateNow(String id, String citizenAccessToken, String reason);

    /**
     * Hệ thống trả ca VERIFYING về hàng đợi khi người giữ mất kết nối quá lâu (watchdog).
     * @return false nếu ca đã đổi (người đó vừa nhả / đã điều động / người khác giữ)
     */
    boolean autoRelease(String id, java.util.UUID handlerId, String note);

    /** Cán bộ giữ ca (VERIFYING) chuyển ca sang lực lượng khác: ca về PENDING ở hàng đợi lực lượng mới. */
    IncidentQueueResponse transferAgency(String id, com.sosvietnam.model.payload.request.TransferIncidentRequest request);

    /** Huỷ các ca chờ quá lâu mà chưa ai nhận (lý do NO_RESPONDER). Trả về số ca đã huỷ. */
    int cancelStaleIncidents();
}
