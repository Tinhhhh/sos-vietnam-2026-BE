package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * GET /api/v1/field/plan: thông tin ca + kế hoạch điều động cho người phụ trách hiện trường (mở link, không đăng nhập).
 * Có SĐT người báo tin để gọi khi tới nơi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldPlanResponse {

    @JsonProperty("incident_id")
    private String incidentId;

    @JsonProperty("incident_status")
    private IncidentStatus incidentStatus;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("emergency_type_label")
    private String emergencyTypeLabel;

    @JsonProperty("incident_tag")
    private String incidentTag;

    @JsonProperty("description")
    private String description;

    @JsonProperty("citizen_name")
    private String citizenName;

    @JsonProperty("citizen_phone")
    private String citizenPhone;

    @JsonProperty("address")
    private String address;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    /** Cán bộ đang giữ ca (gọi về khi cần). */
    @JsonProperty("handler_name")
    private String handlerName;

    @JsonProperty("plan")
    private DispatchPlanResponse plan;
}
