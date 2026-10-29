package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.util.LookupCodes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Những gì người báo tin thấy về ca của mình trên màn chờ (GET /incidents/{id}/tracking,
 * POST /incidents/{id}/cancel). Không có thông tin của người khác ngoài tên cán bộ đang giữ ca.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentTrackingResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("status")
    private IncidentStatus status;

    @JsonProperty("routed_agency")
    private AgencyType routedAgency;

    @JsonProperty("routed_level")
    private UnitLevel routedLevel;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("escalate_at")
    private OffsetDateTime escalateAt;

    @JsonProperty("escalated_at")
    private OffsetDateTime escalatedAt;

    /** Vì sao ca lên tỉnh: cán bộ xã chuyển / quá hạn tự chuyển / người báo yêu cầu (ghi chú nội bộ không trả cho dân). */
    @JsonProperty("escalation_reason")
    private com.sosvietnam.model.payload.enums.EscalationReason escalationReason;

    @JsonProperty("accepted_at")
    private OffsetDateTime acceptedAt;

    /** Cán bộ đã lập văn bản kết thúc "Đã xử lý xong" (xác nhận của cán bộ); ca đóng khi dân cũng xác nhận. */
    @JsonProperty("officer_confirmed_at")
    private OffsetDateTime officerConfirmedAt;

    @JsonProperty("citizen_confirmed_at")
    private OffsetDateTime citizenConfirmedAt;

    @JsonProperty("cancelled_at")
    private OffsetDateTime cancelledAt;

    @JsonProperty("cancel_reason")
    private CancelReason cancelReason;

    @JsonProperty("cancel_reason_label")
    private String cancelReasonLabel;

    @JsonProperty("handler")
    private Handler handler;

    /** Mã ca (XXXX-XXXX) ở mọi trạng thái: dân lưu lại để Tra cứu vào lại ca nếu lỡ đóng trang */
    @JsonProperty("lookup_code")
    private String lookupCode;

    /** Văn bản báo cáo: NONE / AWAITING_CITIZEN / CITIZEN_SIGNED / SIGNED (IncidentDocument.statusOf) */
    @JsonProperty("document_status")
    private String documentStatus;

    /** Lúc ca hoàn tất (RESOLVED), null với ca chưa xong */
    @JsonProperty("resolved_at")
    private OffsetDateTime resolvedAt;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Handler {
        @JsonProperty("full_name")
        private String fullName;
    }

    /** @param handlerName tên cán bộ đang giữ ca, null = chưa ai nhận */
    public static IncidentTrackingResponse from(Incident i, String handlerName) {
        return from(i, handlerName, "NONE");
    }

    public static IncidentTrackingResponse from(Incident i, String handlerName, String documentStatus) {
        return IncidentTrackingResponse.builder()
                .id(i.getId())
                .emergencyType(i.getEmergencyType())
                .status(i.getStatus())
                .routedAgency(i.getRoutedAgency())
                .routedLevel(i.getRoutedLevel())
                .province(i.provinceName())
                .ward(i.wardName())
                .createdAt(withZone(i.getCreatedAt()))
                .escalateAt(withZone(i.getEscalateAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .escalationReason(i.getEscalationReason())
                .acceptedAt(withZone(i.getAcceptedAt()))
                .officerConfirmedAt(withZone(i.getOfficerConfirmedAt()))
                .citizenConfirmedAt(withZone(i.getCitizenConfirmedAt()))
                .cancelledAt(withZone(i.getCancelledAt()))
                .cancelReason(i.getCancelReason())
                .cancelReasonLabel(i.getCancelReason() == null ? null : i.getCancelReason().getLabel())
                .handler(handlerName == null ? null : new Handler(handlerName))
                .lookupCode(LookupCodes.format(i.getLookupCode()))
                .documentStatus(documentStatus)
                .resolvedAt(i.getStatus() == IncidentStatus.RESOLVED ? withZone(i.getUpdatedAt()) : null)
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
