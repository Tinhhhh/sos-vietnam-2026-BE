package com.sosvietnam.model.payload.request;

import com.sosvietnam.model.payload.enums.AgencyType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationRequest {

    private String firstName;
    private String lastName;

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    private String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    private String password;

    private String phone;
    private String address;

    private String roleName; // e.g. "DISPATCHER", "OFFICER", "CITIZEN"
    private AgencyType agencyType;
    private String province;
    private String ward;
}