package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Một ca đã đóng (RESOLVED / CANCELLED) trong cửa sổ "Lịch sử sự cố" (GET /incidents/history).
 * closed_at: lúc huỷ với ca CANCELLED, lần cập nhật cuối với ca RESOLVED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentHistoryResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("status")
    private IncidentStatus status;

    @JsonProperty("status_label")
    private String statusLabel;

    @JsonProperty("routed_agency")
    private AgencyType routedAgency;

    @JsonProperty("routed_level")
    private UnitLevel routedLevel;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("address")
    private String address;

    @JsonProperty("incident_tags")
    private List<String> incidentTags;

    @JsonProperty("citizen_name")
    private String citizenName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("accepted_at")
    private OffsetDateTime acceptedAt;

    @JsonProperty("closed_at")
    private OffsetDateTime closedAt;

    @JsonProperty("cancel_reason")
    private CancelReason cancelReason;

    @JsonProperty("cancel_reason_label")
    private String cancelReasonLabel;

    /** Cán bộ nhận ca gần nhất, null = chưa ai nhận. */
    @JsonProperty("handler_name")
    private String handlerName;

    @JsonProperty("dispatch_count")
    private long dispatchCount;

    public static IncidentHistoryResponse from(Incident i, String handlerName, long dispatchCount) {
        LocalDateTime closed = i.getStatus() == IncidentStatus.CANCELLED && i.getCancelledAt() != null
                ? i.getCancelledAt() : i.getUpdatedAt();
        return IncidentHistoryResponse.builder()
                .id(i.getId())
                .emergencyType(i.getEmergencyType())
                .status(i.getStatus())
                .statusLabel(i.getStatus() == null ? null : i.getStatus().getLabel())
                .routedAgency(i.getRoutedAgency())
                .routedLevel(i.getRoutedLevel())
                .province(i.provinceName())
                .ward(i.wardName())
                .address(i.getAddress())
                .incidentTags(i.tags())
                .citizenName(i.getCitizenName())
                .phone(i.getPhone())
                .createdAt(withZone(i.getCreatedAt()))
                .acceptedAt(withZone(i.getAcceptedAt()))
                .closedAt(withZone(closed))
                .cancelReason(i.getCancelReason())
                .cancelReasonLabel(i.getCancelReason() == null ? null : i.getCancelReason().getLabel())
                .handlerName(handlerName)
                .dispatchCount(dispatchCount)
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
