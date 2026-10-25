package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/** Body của POST /api/v1/incidents/{id}/request-dispatch: người phụ trách hiện trường của đơn vị người giữ ca. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequestDispatchRequest {

    @NotNull(message = "Chưa chọn người phụ trách hiện trường")
    @JsonProperty("field_member_id")
    private UUID fieldMemberId;
}
