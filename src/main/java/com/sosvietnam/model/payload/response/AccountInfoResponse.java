package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Account;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Thông tin tài khoản (GET /api/v1/auth/me, POST /api/v1/admin/accounts).
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

    @JsonProperty(value = "unit_level", index = 9)
    private String unitLevel;

    @JsonProperty(value = "province", index = 10)
    private String province;

    @JsonProperty(value = "ward", index = 11)
    private String ward;

    @JsonProperty(value = "province_id", index = 12)
    private String provinceId;

    @JsonProperty(value = "ward_id", index = 13)
    private Long wardId;

    @JsonProperty(value = "is_locked", index = 14)
    private boolean locked;

    @JsonProperty(value = "created_date", index = 15)
    private LocalDateTime createdDate;

    @JsonProperty(value = "role_id", index = 16)
    private Long roleId;

    @JsonProperty(value = "role_name", index = 17)
    private String roleName;

    @JsonProperty(value = "full_address", index = 18)
    private String fullAddress;

    public static AccountInfoResponse from(Account account) {
        return AccountInfoResponse.builder()
                .accountId(account.getId())
                .lastName(account.getLastName())
                .firstName(account.getFirstName())
                .fullName(account.fullName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .address(account.getAddress())
                .fullAddress(account.fullAddress())
                .agencyType(account.getAgencyType() != null ? account.getAgencyType().name() : null)
                .unitLevel(account.getUnitLevel() != null ? account.getUnitLevel().name() : null)
                .province(account.provinceName())
                .provinceId(account.provinceId())
                .ward(account.wardName())
                .wardId(account.wardId())
                .locked(account.isLocked())
                .createdDate(account.getCreatedDate())
                .roleId(account.getRole() != null ? account.getRole().getId() : null)
                .roleName(account.getRole() != null ? account.getRole().getRoleName() : null)
                .build();
    }
}
