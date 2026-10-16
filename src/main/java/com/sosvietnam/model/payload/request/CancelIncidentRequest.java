package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.CancelReason;
import lombok.Data;

/** Body (tuỳ chọn) của POST /api/v1/incidents/{id}/cancel: người dân huỷ tin báo của mình. */
@Data
public class CancelIncidentRequest {
    // Không bắt buộc; NO_RESPONDER chỉ hệ thống dùng nên bị từ chối ở service
    @JsonProperty("reason")
    private CancelReason reason;
}
