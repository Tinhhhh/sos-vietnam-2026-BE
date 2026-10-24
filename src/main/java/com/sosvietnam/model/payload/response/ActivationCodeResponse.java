package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Mã kích hoạt dùng 1 lần: chỉ trả đúng 1 lần lúc thêm cán bộ / cấp lại PIN (DB chỉ lưu bản băm). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ActivationCodeResponse {

    @JsonProperty("member")
    private UnitMemberResponse member;

    @JsonProperty("activation_code")
    private String activationCode;

    @JsonProperty("activation_expires_at")
    private LocalDateTime activationExpiresAt;
}
