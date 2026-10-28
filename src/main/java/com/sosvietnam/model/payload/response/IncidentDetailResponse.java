package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentMedia;
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
 * Hộp "Chi tiết" của một ca cho cán bộ (GET /incidents/{id}): thông tin ca, ảnh / video người dân gửi
 * (url xem trực tiếp được), đơn vị đã điều động và lịch sử nhận / giao ca.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDetailResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("emergency_type_label")
    private String emergencyTypeLabel;

    @JsonProperty("status")
    private IncidentStatus status;

    @JsonProperty("status_label")
    private String statusLabel;

    @JsonProperty("incident_tags")
    private List<String> incidentTags;

    @JsonProperty("description")
    private String description;

    @JsonProperty("citizen_name")
    private String citizenName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("address")
    private String address;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("province")
    private String province;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("accuracy")
    private Integer accuracy;

    @JsonProperty("routed_agency")
    private AgencyType routedAgency;

    @JsonProperty("routed_level")
    private UnitLevel routedLevel;

    @JsonProperty("handling_unit")
    private String handlingUnit;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("accepted_at")
    private OffsetDateTime acceptedAt;

    @JsonProperty("escalate_at")
    private OffsetDateTime escalateAt;

    @JsonProperty("escalated_at")
    private OffsetDateTime escalatedAt;

    /** Vì sao chuyển lên tỉnh: NO_RESPONSE_TIMEOUT / CITIZEN_REQUEST / OFFICER_REQUEST (V9) */
    /** Lực lượng trước khi chuyển + lý do (V10), null nếu ca chưa chuyển lực lượng */
    @JsonProperty("transferred_from")
    private com.sosvietnam.model.payload.enums.AgencyType transferredFrom;

    @JsonProperty("transferred_from_label")
    private String transferredFromLabel;

    @JsonProperty("transfer_reason")
    private String transferReason;

    @JsonProperty("escalation_reason")
    private com.sosvietnam.model.payload.enums.EscalationReason escalationReason;

    @JsonProperty("escalation_reason_label")
    private String escalationReasonLabel;

    @JsonProperty("report_type")
    private com.sosvietnam.model.payload.enums.ReportType reportType;

    @JsonProperty("report_type_label")
    private String reportTypeLabel;

    /** Số ca báo khống 24 giờ qua từ cùng IP (cờ cảnh báo, không chặn); 0 = không có. */
    @JsonProperty("ip_false_alarm_count")
    private long ipFalseAlarmCount;

    /** Lý do cán bộ xã ghi khi tự chuyển lên tỉnh */
    @JsonProperty("escalation_note")
    private String escalationNote;

    @JsonProperty("cancelled_at")
    private OffsetDateTime cancelledAt;

    @JsonProperty("cancel_reason")
    private CancelReason cancelReason;

    @JsonProperty("cancel_reason_label")
    private String cancelReasonLabel;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @JsonProperty("media")
    private List<IncidentResponse.MediaResponse> media;

    @JsonProperty("dispatches")
    private List<Dispatch> dispatches;

    @JsonProperty("claims")
    private List<Claim> claims;

    /** Ca khác đang mở của cùng số điện thoại. */
    @JsonProperty("other_open_cases")
    private List<IncidentQueueResponse.OpenCase> otherOpenCases;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Dispatch {
        @JsonProperty("station_name")
        private String stationName;

        @JsonProperty("agency_label")
        private String agencyLabel;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("dispatched_by")
        private String dispatchedBy;

        @JsonProperty("note")
        private String note;

        @JsonProperty("dispatched_at")
        private OffsetDateTime dispatchedAt;
    }

    /** Một lượt giữ ca: ai nhận, lúc nào, kết thúc thế nào (trả lại / giao / leo thang / đóng ca). */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Claim {
        @JsonProperty("officer_name")
        private String officerName;

        @JsonProperty("claimed_at")
        private OffsetDateTime claimedAt;

        @JsonProperty("released_at")
        private OffsetDateTime releasedAt;

        @JsonProperty("end_reason_label")
        private String endReasonLabel;

        @JsonProperty("handed_to_name")
        private String handedToName;

        @JsonProperty("note")
        private String note;
    }

    public static IncidentDetailResponse from(Incident i, List<IncidentMedia> media, List<IncidentDispatch> dispatches,
                                              List<IncidentClaim> claims) {
        String unit = i.getRoutedAgency() == null ? null
                : i.getRoutedAgency().getLabel()
                + (i.getRoutedLevel() == null ? "" : " · " + i.getRoutedAgency().levelLabel(i.getRoutedLevel()));
        return IncidentDetailResponse.builder()
                .id(i.getId())
                .emergencyType(i.getEmergencyType())
                .emergencyTypeLabel(i.getEmergencyType() == null ? null : i.getEmergencyType().getLabel())
                .status(i.getStatus())
                .statusLabel(i.getStatus() == null ? null : i.getStatus().getLabel())
                .incidentTags(i.tags())
                .description(i.getDescription())
                .citizenName(i.getCitizenName())
                .phone(i.getPhone())
                .address(i.getAddress())
                .ward(i.wardName())
                .province(i.provinceName())
                .latitude(i.getLatitude())
                .longitude(i.getLongitude())
                .accuracy(i.getAccuracy())
                .routedAgency(i.getRoutedAgency())
                .routedLevel(i.getRoutedLevel())
                .handlingUnit(unit)
                .createdAt(withZone(i.getCreatedAt()))
                .acceptedAt(withZone(i.getAcceptedAt()))
                .escalateAt(withZone(i.getEscalateAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .transferredFrom(i.getTransferredFrom())
                .transferredFromLabel(i.getTransferredFrom() == null ? null : i.getTransferredFrom().getLabel())
                .transferReason(i.getTransferReason())
                .escalationReason(i.getEscalationReason())
                .escalationReasonLabel(i.getEscalationReason() == null ? null : i.getEscalationReason().getLabel())
                .reportType(i.getReportType())
                .reportTypeLabel(i.getReportType() == null ? null : i.getReportType().getLabel())
                .escalationNote(i.getEscalationNote())
                .cancelledAt(withZone(i.getCancelledAt()))
                .cancelReason(i.getCancelReason())
                .cancelReasonLabel(i.getCancelReason() == null ? null : i.getCancelReason().getLabel())
                .updatedAt(withZone(i.getUpdatedAt()))
                .media(media.stream().map(m -> IncidentResponse.toMediaResponse(i.getId(), m)).toList())
                .dispatches(dispatches.stream().map(d -> Dispatch.builder()
                        .stationName(d.getStation().getName())
                        .agencyLabel(d.getStation().getAgencyType() == null ? null : d.getStation().getAgencyType().getLabel())
                        .phone(d.getStation().getPhone())
                        .dispatchedBy(d.getDispatchedBy() == null ? null : d.getDispatchedBy().displayName())
                        .note(d.getNote())
                        .dispatchedAt(withZone(d.getDispatchedAt()))
                        .build()).toList())
                .claims(claims.stream().map(c -> Claim.builder()
                        .officerName(c.getMember() == null ? null : c.getMember().displayName())
                        .claimedAt(withZone(c.getClaimedAt()))
                        .releasedAt(withZone(c.getReleasedAt()))
                        .endReasonLabel(c.getEndReason() == null ? null : c.getEndReason().getLabel())
                        .handedToName(c.getHandedTo() == null ? null : c.getHandedTo().displayName())
                        .note(c.getNote())
                        .build()).toList())
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
