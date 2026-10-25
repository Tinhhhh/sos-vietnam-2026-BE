package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/** Body của POST /api/v1/incidents/{id}/dispatch-plan/delegate: cán bộ của đơn vị được uỷ quyền. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DelegateRequest {

    @NotNull(message = "Chưa chọn cán bộ nhận")
    @JsonProperty("member_id")
    private UUID memberId;
}
