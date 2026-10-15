package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thông tin tài khoản đang đăng nhập (GET /api/v1/auth/me).
 * Thay cho việc trả thẳng entity Account: không lộ password hash,
 * role trả về dạng chuỗi (role_name) thay vì nguyên object Role.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AccountInfoResponse {

    @JsonProperty(value = "account_id", index = 1)
    private UUID accountId;

    @JsonProperty(value = "last_name", index = 2)
    private String lastName;

    @JsonProperty(value = "first_name", index = 3)
    private String firstName;

    @JsonProperty(value = "full_name", index = 4)
    private String fullName;

    @JsonProperty(value = "email", index = 5)
    private String email;

    @JsonProperty(value = "phone", index = 6)
    private String phone;

    @JsonProperty(value = "address", index = 7)
    private String address;

    @JsonProperty(value = "agency_type", index = 8)
    private String agencyType;

    @JsonProperty(value = "province", index = 9)
    private String province;

    @JsonProperty(value = "ward", index = 10)
    private String ward;

    @JsonProperty(value = "is_locked", index = 11)
    private boolean locked;

    @JsonProperty(value = "created_date", index = 12)
    private LocalDateTime createdDate;

    @JsonProperty(value = "role_id", index = 13)
    private Long roleId;

    @JsonProperty(value = "role_name", index = 14)
    private String roleName;
}
