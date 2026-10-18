package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.util.LookupCodes;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Thông tin cứu hộ người dân xem lại bằng mã tra cứu (POST /incidents/lookup).
 * Không có incidents.id, SĐT, toạ độ hay tên cán bộ: chỉ đơn vị xử lý và lực lượng được điều động.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentLookupResponse {

    @JsonProperty("lookup_code")
    private String lookupCode;

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

    @JsonProperty("address")
    private String address;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("province")
    private String province;

    /** Đơn vị giữ ca, vd "Công An · Cấp Xã/Phường" */
    @JsonProperty("handling_unit")
    private String handlingUnit;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("accepted_at")
    private OffsetDateTime acceptedAt;

    @JsonProperty("escalated_at")
    private OffsetDateTime escalatedAt;

    @JsonProperty("cancelled_at")
    private OffsetDateTime cancelledAt;

    @JsonProperty("cancel_reason_label")
    private String cancelReasonLabel;

    @JsonProperty("dispatches")
    private List<Dispatch> dispatches;

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

        @JsonProperty("dispatched_at")
        private OffsetDateTime dispatchedAt;
    }

    public static IncidentLookupResponse from(Incident i, List<IncidentDispatch> dispatches) {
        String unit = i.getRoutedAgency() == null ? null
                : i.getRoutedAgency().getLabel()
                + (i.getRoutedLevel() == null ? "" : " · " + i.getRoutedAgency().levelLabel(i.getRoutedLevel()));
        return IncidentLookupResponse.builder()
                .lookupCode(LookupCodes.format(i.getLookupCode()))
                .emergencyType(i.getEmergencyType())
                .emergencyTypeLabel(i.getEmergencyType() == null ? null : i.getEmergencyType().getLabel())
                .status(i.getStatus())
                .statusLabel(i.getStatus() == null ? null : i.getStatus().getLabel())
                .incidentTags(i.tags())
                .address(i.getAddress())
                .ward(i.wardName())
                .province(i.provinceName())
                .handlingUnit(unit)
                .createdAt(withZone(i.getCreatedAt()))
                .acceptedAt(withZone(i.getAcceptedAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .cancelledAt(withZone(i.getCancelledAt()))
                .cancelReasonLabel(i.getCancelReason() == null ? null : i.getCancelReason().getLabel())
                .dispatches(dispatches.stream().map(d -> Dispatch.builder()
                        .stationName(d.getStation().getName())
                        .agencyLabel(d.getStation().getAgencyType() == null ? null : d.getStation().getAgencyType().getLabel())
                        .phone(d.getStation().getPhone())
                        .dispatchedAt(withZone(d.getDispatchedAt()))
                        .build()).toList())
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
