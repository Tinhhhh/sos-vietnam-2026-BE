package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

/**
 * Body (tuỳ chọn) của POST /api/v1/incidents/{id}/request-dispatch: người phụ trách hiện trường của đơn vị người giữ ca.
 * Thường để trống: người phụ trách chọn ở bước 2 "Điều động" của cửa sổ điều động.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDispatchRequest {

    @JsonProperty("field_member_id")
    private UUID fieldMemberId;
}
