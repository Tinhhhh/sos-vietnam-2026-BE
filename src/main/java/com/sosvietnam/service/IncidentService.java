package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.request.ReleaseIncidentRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.HandoverCandidateResponse;
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
    ListResponse getQueue(int pageNo, int pageSize);

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

    /** Người báo tin tự huỷ ca còn mở (kể cả khi đã có cán bộ nhận). */
    IncidentTrackingResponse cancelByCitizen(String id, String citizenAccessToken, CancelIncidentRequest request);

    /** Huỷ các ca chờ quá lâu mà chưa ai nhận (lý do NO_RESPONDER). Trả về số ca đã huỷ. */
    int cancelStaleIncidents();
}
