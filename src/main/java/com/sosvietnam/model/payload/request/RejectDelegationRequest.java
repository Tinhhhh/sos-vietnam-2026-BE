package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Body của POST /api/v1/incidents/{id}/dispatch-plan/reject: lý do đơn vị được uỷ quyền từ chối. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectDelegationRequest {

    @NotBlank(message = "Vui lòng nhập lý do từ chối")
    @Size(min = 5, max = 255, message = "Lý do từ 5 đến 255 ký tự")
    @JsonProperty("reason")
    private String reason;
}
