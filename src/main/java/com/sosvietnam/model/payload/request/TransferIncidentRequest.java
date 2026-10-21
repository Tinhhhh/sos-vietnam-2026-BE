package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.AgencyType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Body của POST /api/v1/incidents/{id}/transfer: chuyển ca đang giữ (VERIFYING) sang lực lượng khác. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferIncidentRequest {

    @NotNull(message = "Chọn lực lượng nhận ca")
    @JsonProperty("agency")
    private AgencyType agency;

    @Size(max = 500, message = "Lý do tối đa 500 ký tự")
    @JsonProperty("reason")
    private String reason;
}
