package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentReport;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.UnitLevel;
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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thông tin cứu hộ người dân xem lại bằng mã tra cứu (POST /incidents/lookup).
 * Người tra cứu phải có mã + 4 số cuối SĐT (chính người báo tin). Không trả incidents.id, SĐT đầy đủ (chỉ bản che)
 * hay SĐT cá nhân của cán bộ; cán bộ chỉ có họ tên + đơn vị.
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

    /** Người báo tin */
    @JsonProperty("citizen_name")
    private String citizenName;

    /** SĐT đã che, vd "091****678" */
    @JsonProperty("phone_masked")
    private String phoneMasked;

    @JsonProperty("description")
    private String description;

    /** Địa chỉ đầy đủ nơi báo tin (không lặp xã/tỉnh) */
    @JsonProperty("full_address")
    private String fullAddress;

    /** Vị trí lực lượng tới (điểm đến đã chốt khi điều động), null nếu chưa điều động */
    @JsonProperty("destination_address")
    private String destinationAddress;

    /** Cán bộ nhận ca (lượt giữ ca gần nhất) */
    @JsonProperty("handler")
    private Officer handler;

    /** Văn bản kết thúc ca, null nếu ca chưa đóng bằng văn bản */
    @JsonProperty("report")
    private Report report;

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

    /** Vì sao chuyển lên tỉnh: NO_RESPONSE_TIMEOUT / CITIZEN_REQUEST / OFFICER_REQUEST (V9) */
    @JsonProperty("escalation_reason")
    private com.sosvietnam.model.payload.enums.EscalationReason escalationReason;

    @JsonProperty("escalation_reason_label")
    private String escalationReasonLabel;

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
    public static class Officer {
        @JsonProperty("name")
        private String name;

        /** vd "Công An · Cấp Xã/Phường · Phường Tân An" */
        @JsonProperty("unit")
        private String unit;

        @JsonProperty("at")
        private OffsetDateTime at;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Report {
        @JsonProperty("result_label")
        private String resultLabel;

        @JsonProperty("summary")
        private String summary;

        @JsonProperty("forces")
        private String forces;

        @JsonProperty("note")
        private String note;

        @JsonProperty("created_at")
        private OffsetDateTime createdAt;
    }

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Dispatch {
        @JsonProperty("role_label")
        private String roleLabel;

        @JsonProperty("status_label")
        private String statusLabel;

        @JsonProperty("station_name")
        private String stationName;

        @JsonProperty("agency_label")
        private String agencyLabel;

        @JsonProperty("phone")
        private String phone;

        @JsonProperty("dispatched_at")
        private OffsetDateTime dispatchedAt;

        /** Cán bộ điều động (người giữ ca) */
        @JsonProperty("dispatched_by")
        private Officer dispatchedBy;

        /** Cán bộ đơn vị được uỷ quyền, at = lúc trả lời nhận/từ chối */
        @JsonProperty("assigned_to")
        private Officer assignedTo;

        @JsonProperty("reject_reason")
        private String rejectReason;

        /** Điểm xuất phát (trạm hoặc điểm chọn trên bản đồ) */
        @JsonProperty("origin_address")
        private String originAddress;

        @JsonProperty("destination_address")
        private String destinationAddress;

        @JsonProperty("distance_m")
        private Integer distanceM;

        @JsonProperty("duration_s")
        private Integer durationS;

        @JsonProperty("departed_at")
        private OffsetDateTime departedAt;

        @JsonProperty("arrived_at")
        private OffsetDateTime arrivedAt;
    }

    public static IncidentLookupResponse from(Incident i, List<IncidentDispatch> dispatches, IncidentClaim lastClaim,
                                              IncidentReport report, Map<Long, IncidentRoute> routes) {
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
                .citizenName(i.getCitizenName())
                .phoneMasked(maskPhone(i.getPhone()))
                .description(i.getDescription())
                .fullAddress(joinAddress(i.getAddress(), i.wardName(), i.provinceName()))
                .destinationAddress(dedupeParts(i.getDestAddress()))
                .handler(lastClaim == null ? null : officer(lastClaim.getMember(), lastClaim.getClaimedAt()))
                .report(report == null ? null : Report.builder()
                        .resultLabel(report.getResult() == null ? null : report.getResult().getLabel())
                        .summary(report.getSummary())
                        .forces(report.getForces())
                        .note(report.getNote())
                        .createdAt(withZone(report.getCreatedAt()))
                        .build())
                .address(i.getAddress())
                .ward(i.wardName())
                .province(i.provinceName())
                .handlingUnit(unit)
                .createdAt(withZone(i.getCreatedAt()))
                .acceptedAt(withZone(i.getAcceptedAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .escalationReason(i.getEscalationReason())
                .escalationReasonLabel(i.getEscalationReason() == null ? null : i.getEscalationReason().getLabel())
                .cancelledAt(withZone(i.getCancelledAt()))
                .cancelReasonLabel(i.getCancelReason() == null ? null : i.getCancelReason().getLabel())
                .dispatches(dispatches.stream().map(d -> {
                    IncidentRoute route = routes == null || d.getId() == null ? null : routes.get(d.getId());
                    RescueStation st = d.getStation();
                    return Dispatch.builder()
                            .roleLabel(d.getRole() == null ? null : d.getRole().getLabel())
                            .statusLabel(d.getStatus() == null ? null : d.getStatus().getLabel())
                            .stationName(st.getName())
                            .agencyLabel(st.getAgencyType() == null ? null : st.getAgencyType().getLabel())
                            .phone(st.getPhone())
                            .dispatchedAt(withZone(d.getDispatchedAt()))
                            .dispatchedBy(officer(d.getDispatchedBy(), d.getDispatchedAt()))
                            .assignedTo(officer(d.getAssignedTo(), d.getRespondedAt()))
                            .rejectReason(d.getRejectReason())
                            .originAddress(route != null && route.getOriginAddress() != null ? dedupeParts(route.getOriginAddress())
                                    : joinAddress(st.getAddress(), st.getWard() == null ? null : st.getWard().getName(),
                                    st.getProvince() == null ? null : st.getProvince().getName()))
                            .destinationAddress(route == null ? null : dedupeParts(route.getDestAddress()))
                            .distanceM(route == null ? null : route.getDistanceM())
                            .durationS(route == null ? null : route.getDurationS())
                            .departedAt(withZone(d.getDepartedAt()))
                            .arrivedAt(withZone(d.getArrivedAt()))
                            .build();
                }).toList())
                .build();
    }

    private static Officer officer(com.sosvietnam.model.entity.UnitMember m, LocalDateTime at) {
        if (m == null) return null;
        Account a = m.getAccount();
        String unit = null;
        if (a.getAgencyType() != null) {
            String area = a.getUnitLevel() == UnitLevel.WARD ? a.wardName()
                    : a.getUnitLevel() == UnitLevel.PROVINCE ? a.provinceName() : null;
            unit = Stream.of(a.getAgencyType().getLabel(),
                            a.getUnitLevel() == null ? null : a.getAgencyType().levelLabel(a.getUnitLevel()), area)
                    .filter(s -> s != null && !s.isBlank()).collect(Collectors.joining(" · "));
        }
        return Officer.builder().name(m.displayName()).unit(unit).at(withZone(at)).build();
    }

    /** "0912345678" -> "091****678" */
    public static String maskPhone(String phone) {
        if (phone == null) return null;
        String p = phone.trim();
        if (p.length() < 7) return "****";
        return p.substring(0, 3) + "*".repeat(p.length() - 6) + p.substring(p.length() - 3);
    }

    /** "Phường Tân An, Cần Thơ, Phường Tân An, Cần Thơ" -> "Phường Tân An, Cần Thơ" (địa chỉ cũ do FE ghép lặp) */
    public static String dedupeParts(String address) {
        if (address == null || address.isBlank()) return null;
        java.util.LinkedHashMap<String, String> parts = new java.util.LinkedHashMap<>();
        for (String part : address.split(",")) {
            String t = part.trim();
            if (!t.isEmpty()) parts.putIfAbsent(t.toLowerCase(), t);
        }
        return String.join(", ", parts.values());
    }

    /** Ghép địa chỉ + xã + tỉnh, bỏ phần đã có sẵn trong địa chỉ (địa chỉ ca thường đã chứa tên xã/tỉnh). */
    public static String joinAddress(String address, String ward, String province) {
        String base = address == null ? "" : address.trim();
        String lower = base.toLowerCase();
        String tail = Stream.of(ward, province).filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty() && !lower.contains(s.toLowerCase()))
                .collect(Collectors.joining(", "));
        String out = Stream.of(base, tail).filter(s -> !s.isEmpty()).collect(Collectors.joining(", "));
        return out.isEmpty() ? null : out;
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
