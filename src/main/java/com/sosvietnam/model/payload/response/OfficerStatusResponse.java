package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Một cán bộ trong danh sách "Trạng thái cán bộ" (GET /officers/status).
 * online = đang mở bàn chỉ huy (có kết nối STOMP); busy = đang giữ một ca (held_incident).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficerStatusResponse {

    @JsonProperty("member_id")
    private UUID memberId;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("email")
    private String email;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("agency_type")
    private AgencyType agencyType;

    @JsonProperty("unit_level")
    private UnitLevel unitLevel;

    /** "Công An · Cấp Xã/Phường" */
    @JsonProperty("unit_label")
    private String unitLabel;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("online")
    private boolean online;

    @JsonProperty("busy")
    private boolean busy;

    /** Chính người đang xem. */
    @JsonProperty("is_me")
    private boolean me;

    @JsonProperty("held_incident")
    private HeldIncident heldIncident;

    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class HeldIncident {
        @JsonProperty("id")
        private String id;

        @JsonProperty("status")
        private IncidentStatus status;

        @JsonProperty("status_label")
        private String statusLabel;

        @JsonProperty("emergency_type")
        private EmergencyType emergencyType;
    }
}
