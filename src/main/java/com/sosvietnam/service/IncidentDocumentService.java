package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.IncidentDocumentRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.SignDocumentRequest;
import com.sosvietnam.model.payload.response.IncidentDocumentResponse;

/** Văn bản báo cáo "Phiếu thông báo và tiếp nhận thông tin ứng phó sự cố khẩn cấp" của ca đã hoàn tất. */
public interface IncidentDocumentService {

    /** Cán bộ xem văn bản; chưa lưu thì trả bản nháp tự điền (saved = false) */
    IncidentDocumentResponse get(String incidentId);

    /** Cán bộ lưu / sửa nội dung (chưa đủ chữ ký). Sửa nội dung đã có chữ ký thì xoá chữ ký cũ. */
    IncidentDocumentResponse save(String incidentId, IncidentDocumentRequest request);

    /** Cán bộ ký ô "Cán bộ / chỉ huy tiếp nhận" */
    IncidentDocumentResponse signOfficer(String incidentId, SignDocumentRequest request);

    /** Cán bộ ký hộ ô người dân (lý do + xác nhận được uỷ quyền) */
    IncidentDocumentResponse signProxy(String incidentId, SignDocumentRequest request);

    /** File Word (.docx) của văn bản đã lưu */
    byte[] docx(String incidentId);

    /** Người dân xem văn bản của ca mình (citizen token); chưa có văn bản -> 404 */
    IncidentDocumentResponse citizenGet(String incidentId, String citizenToken);

    IncidentDocumentResponse citizenSign(String incidentId, String citizenToken, SignDocumentRequest request);

    /** Người dân xem / ký qua mã tra cứu + 4 số cuối SĐT */
    IncidentDocumentResponse lookupGet(IncidentLookupRequest request);

    IncidentDocumentResponse lookupSign(SignDocumentRequest request);
}
