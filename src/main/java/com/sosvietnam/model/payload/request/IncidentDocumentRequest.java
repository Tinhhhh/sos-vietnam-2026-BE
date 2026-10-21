package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body của PUT /api/v1/incidents/{id}/document: nội dung văn bản báo cáo (cán bộ sửa trực tiếp trên tờ phiếu).
 * Field để trống / null thì lưu trống (không tự điền lại).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDocumentRequest {

    @Size(max = 200) @JsonProperty("header_upper")
    private String headerUpper;

    @Size(max = 200) @JsonProperty("header_lower")
    private String headerLower;

    @Size(max = 200) @JsonProperty("place_date")
    private String placeDate;

    @Size(max = 200) @JsonProperty("officer_name")
    private String officerName;

    @Size(max = 200) @JsonProperty("officer_title")
    private String officerTitle;

    @Size(max = 300) @JsonProperty("unit_name")
    private String unitName;

    @Size(max = 500) @JsonProperty("unit_address")
    private String unitAddress;

    @Size(max = 100) @JsonProperty("unit_phone")
    private String unitPhone;

    @Size(max = 200) @JsonProperty("reporter_name")
    private String reporterName;

    @Size(max = 30) @JsonProperty("reporter_phone")
    private String reporterPhone;

    @Size(max = 50) @JsonProperty("target_type")
    private String targetType;

    @Size(max = 100) @JsonProperty("incident_time")
    private String incidentTime;

    @Size(max = 500) @JsonProperty("incident_address")
    private String incidentAddress;

    @Size(max = 100) @JsonProperty("coordinates")
    private String coordinates;

    @Size(max = 3000) @JsonProperty("description")
    private String description;

    @JsonProperty("affects_citizens")
    private Boolean affectsCitizens;

    @JsonProperty("needs_help")
    private Boolean needsHelp;

    @Size(max = 2000) @JsonProperty("help_request")
    private String helpRequest;

    @Size(max = 2000) @JsonProperty("advice_given")
    private String adviceGiven;

    @Size(max = 3000) @JsonProperty("resolution_result")
    private String resolutionResult;
}
