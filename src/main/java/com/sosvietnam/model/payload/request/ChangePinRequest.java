package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/** Body của POST /api/v1/auth/pin (cán bộ đang trực tự đổi PIN). */
@Data
public class ChangePinRequest {

    @NotBlank(message = "Nhập PIN hiện tại")
    @JsonProperty("old_pin")
    private String oldPin;

    @NotBlank(message = "Nhập PIN mới")
    @Pattern(regexp = "^\\d{4,6}$", message = "PIN mới gồm 4-6 chữ số")
    @JsonProperty("new_pin")
    private String newPin;
}
