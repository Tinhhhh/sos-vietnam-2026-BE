package com.sosvietnam.service;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.response.IncidentQueueResponse;
import com.sosvietnam.security.OfficerPrincipal;

import java.util.Collection;
import java.util.List;
import java.util.Set;

/** Gửi tin realtime (STOMP /user/queue/incidents) về sự cố cho đúng các trực ban có quyền xem. */
public interface IncidentNotifier {

    /** Gửi ngay; nếu đang trong transaction thì đợi commit xong mới gửi (để không báo một thay đổi bị rollback). */
    void notify(String incidentId, IncidentEvent event);

    /** Chỉ báo màn theo dõi của người báo tin (vd văn bản báo cáo vừa được lập / ký), không đụng hàng đợi cán bộ. */
    void notifyCitizen(String incidentId);

    /** Gỡ thẻ ca khỏi hàng đợi của cán bộ lực lượng cũ (sau khi ca chuyển sang lực lượng khác). */
    void notifyRemovedFor(String incidentId, com.sosvietnam.model.payload.enums.AgencyType previousAgency);

    /** Cán bộ đang kết nối màn hình trực ban (mỗi phiên STOMP 1 cán bộ; chỉ đúng khi 1 instance BE). */
    List<OfficerPrincipal> onlineOfficers();

    /** member id của các cán bộ đang kết nối. */
    Set<java.util.UUID> onlineMemberIds();

    /** Đổi ca sang dạng hàng đợi theo góc nhìn của một trực ban (kèm tên người đang giữ ca). */
    List<IncidentQueueResponse> toQueueViews(Collection<Incident> incidents, OfficerPrincipal viewer);
}
