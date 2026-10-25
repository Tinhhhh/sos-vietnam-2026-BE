package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

/** Body của POST /api/v1/unit/duty (giao trực ban cho cán bộ khác của đơn vị). */
@Data
public class AssignDutyRequest {

    @NotNull(message = "Chọn cán bộ nhận trực ban")
    @JsonProperty("member_id")
    private UUID memberId;
}
