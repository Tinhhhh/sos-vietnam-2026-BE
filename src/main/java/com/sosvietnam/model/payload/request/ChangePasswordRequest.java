package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Body của POST /api/v1/auth/change-password (người đang đăng nhập tự đổi mật khẩu). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    @NotBlank(message = "Vui lòng nhập mật khẩu hiện tại")
    @JsonProperty("current_password")
    private String currentPassword;

    @NotBlank(message = "Vui lòng nhập mật khẩu mới")
    @Size(min = 8, max = 100, message = "Mật khẩu mới phải từ 8 đến 100 ký tự")
    @JsonProperty("new_password")
    private String newPassword;
}
