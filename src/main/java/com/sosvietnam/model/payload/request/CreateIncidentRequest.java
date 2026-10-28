package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.EmergencyType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/** Body của POST /api/v1/incidents: người dân gửi tin báo SOS kèm toạ độ GPS. */
@Data
public class CreateIncidentRequest {
    @Size(max = 150)
    @JsonProperty("citizen_name")
    private String citizenName;
    // Cùng quy tắc với Incident.phone (cột 10 ký tự): số sai trả 400 ở đây thay vì lỗi 500 lúc lưu DB
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải gồm đúng 10 chữ số, bắt đầu bằng 0 (ví dụ 0912345678)")
    @JsonProperty("phone")
    private String phone;
    @NotNull
    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;
    /** IP người gửi, controller điền từ request (không nhận từ JSON). */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String clientIp;
    /** Tự báo (mặc định khi bỏ trống) / báo hộ (chưa mở, 400). */
    @JsonProperty("report_type")
    private com.sosvietnam.model.payload.enums.ReportType reportType;
    /**
     * Số đã có 1 tin báo đang mở (409 OPEN_CASE_EXISTS) mà dân chọn "Báo sự việc khác": true = tạo ca mới.
     * Tối đa 2 ca mở / số (docs/spec-multi-case.md).
     */
    @JsonProperty("new_case")
    private Boolean newCase;
    @Size(max = 10)
    @JsonProperty("incident_tags")
    private List<@Size(max = 200) String> incidentTags;
    @Size(max = 2000)
    @JsonProperty("description")
    private String description;
    @Size(max = 500)
    @JsonProperty("address")
    private String address;
    @NotNull
    @DecimalMin("-90")
    @DecimalMax("90")
    @JsonProperty("latitude")
    private Double latitude;
    @NotNull
    @DecimalMin("-180")
    @DecimalMax("180")
    @JsonProperty("longitude")
    private Double longitude;
    @PositiveOrZero
    @JsonProperty("accuracy")
    private Integer accuracy;
}
