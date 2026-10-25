package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * Kết quả trả về khi đăng nhập / refresh token: chỉ có cặp token.
 * Tài khoản đơn vị đăng nhập thì chỉ có unit_token (token tạm để chọn cán bộ + nhập PIN).
 * Thông tin tài khoản lấy qua GET /api/v1/auth/me (AccountInfoResponse).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
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

    @Schema(description = "Token tạm của tài khoản đơn vị (vài phút), dùng cho /auth/unit/*")
    @JsonProperty("unit_token")
    private String unitToken;
}
