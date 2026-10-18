package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Body của POST /api/v1/incidents/{id}/release (body có thể bỏ trống).
 * to_account_id trống = trả ca về hàng đợi chung; có giá trị = giao thẳng cho cán bộ đó (cùng đơn vị, đang rảnh).
 * note: lý do (bắt buộc khi giao ca), lưu vào incident_claims.note của lượt giữ ca vừa kết thúc.
 */
@Data
public class ReleaseIncidentRequest {
    @JsonProperty("to_account_id")
    private UUID toAccountId;

    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    @JsonProperty("note")
    private String note;
}
