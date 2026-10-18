package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body của POST /api/v1/auth/register (đăng ký công khai).
 * Không còn field roleName / agencyType: client không được tự chọn quyền hay
 * lực lượng, tài khoản đăng ký công khai luôn là CITIZEN. Tài khoản cán bộ
 * (OFFICER) sẽ do ADMIN tạo / import từ Excel.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {

    @JsonProperty("first_name")
    @Size(max = 100)
    private String firstName;

    @JsonProperty("last_name")
    @Size(max = 100)
    private String lastName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
    private String password;

    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0")
    private String phone;

    private String address;

    /** Mã tỉnh (provinces.code), không bắt buộc. */
    @JsonProperty("province_id")
    private String provinceId;

    /** wards.id, phải thuộc province_id. */
    @JsonProperty("ward_id")
    private Long wardId;
}
