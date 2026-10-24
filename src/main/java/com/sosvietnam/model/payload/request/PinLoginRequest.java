package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.UUID;

/**
 * Body của POST /api/v1/auth/unit/pin (cán bộ vào trực) và /auth/unit/activate (đặt PIN lần đầu, kèm activation_code).
 * Header: Authorization: Bearer (unit_token).
 */
@Data
public class PinLoginRequest {

    @NotNull(message = "Chọn cán bộ")
    @JsonProperty("member_id")
    private UUID memberId;

    @NotBlank(message = "Nhập mã PIN")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN gồm 4-6 chữ số")
    @JsonProperty("pin")
    private String pin;

    @JsonProperty("activation_code")
    private String activationCode;
}
