package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Kết quả trả về khi đăng nhập / refresh token: chỉ có cặp token.
 * Thông tin tài khoản lấy qua GET /api/v1/auth/me (AccountInfoResponse).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response object for user sign in")
public class AuthenticationResponse {

    @Schema(description = "Access Token", example = "xxx.yyy.zzz")
    @JsonProperty("access_token")
    private String accessToken;

    @Schema(description = "Refresh Token", example = "xxx.yyy.zzz")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
