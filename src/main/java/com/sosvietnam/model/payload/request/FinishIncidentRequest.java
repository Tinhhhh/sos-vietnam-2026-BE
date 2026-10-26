package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.ReportResult;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body của POST /api/v1/incidents/{id}/dispatch-plan/finish: văn bản kết thúc ca.
 * force = false: bước 4 bình thường (cả cán bộ và người dân đã xác nhận xử lý xong), result RESOLVED / FALSE_ALARM,
 * summary bắt buộc (>= 10 ký tự). force = true: cán bộ giữ ca kết thúc bắt buộc, note là lý do (>= 5 ký tự).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinishIncidentRequest {

    @JsonProperty("result")
    private ReportResult result;

    @Size(max = 2000)
    @JsonProperty("summary")
    private String summary;

    @Size(max = 500)
    @JsonProperty("forces")
    private String forces;

    @Size(max = 1000)
    @JsonProperty("note")
    private String note;

    @JsonProperty("force")
    private boolean force;

    /**
     * Không còn dùng (25/10/2026: cán bộ đóng ca không cần chờ dân xác nhận). Giữ trường để FE cũ gửi lên không bị 400.
     */
    @JsonProperty("skip_confirm")
    private boolean skipConfirm;
}
