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

    /** Email các trực ban đang kết nối màn hình trực ban. */
    Set<String> onlineEmails();

    /** Đổi ca sang dạng hàng đợi theo góc nhìn của một trực ban (kèm tên người đang giữ ca). */
    List<IncidentQueueResponse> toQueueViews(Collection<Incident> incidents, OfficerPrincipal viewer);
}
