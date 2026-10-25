package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

/** Body của POST .../dispatch-plan/depart và PUT .../dispatch-plan/field-member: người phụ trách hiện trường. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldMemberRequest {

    @NotNull(message = "Chưa chọn người phụ trách hiện trường")
    @JsonProperty("member_id")
    private UUID memberId;
}
