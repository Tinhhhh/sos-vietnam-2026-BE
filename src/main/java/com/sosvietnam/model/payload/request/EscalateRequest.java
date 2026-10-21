package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body (tuỳ chọn) của POST /api/v1/incidents/{id}/escalate. Cán bộ giữ ca bắt buộc ghi lý do (5-500 ký tự);
 * người báo tin (citizen token) không cần gửi body.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EscalateRequest {

    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    @JsonProperty("reason")
    private String reason;
}
