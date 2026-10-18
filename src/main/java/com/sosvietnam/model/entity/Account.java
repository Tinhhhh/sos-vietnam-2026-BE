package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "accounts", indexes = {
        @Index(name = "idx_account_email", columnList = "email")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "account_id")
    private UUID id;

    @Column(name = "first_name", length = 100)
    private String firstName;

    @Column(name = "last_name", length = 100)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false, length = 100)
    @Email
    private String email;

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    @Column(name = "phone", length = 10)
    @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must be 10 digits starting with 0.")
    private String phone;

    @Column(name = "address", columnDefinition = "TEXT")
    private String address;

    @Builder.Default
    @Column(name = "is_locked", nullable = false)
    private boolean isLocked = false;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency_type", length = 30)
    private AgencyType agencyType;

    // Cấp đơn vị trực ban (xã/phường, tỉnh, Bộ); null với tài khoản người dân
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_level", length = 20)
    private UnitLevel unitLevel;

    // Địa bàn đơn vị: 1 tài khoản thuộc 1 tỉnh, 1 xã (cấp tỉnh: xã nơi đặt trụ sở).
    // Cấp nhận ca phân biệt bằng unit_level, không dựa vào việc có ward hay không. Admin toàn quốc: có thể null.
    @ManyToOne
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @Column(name = "created_date", updatable = false)
    private LocalDateTime createdDate;

    @PrePersist
    public void prePersist() {
        if (this.createdDate == null)
            this.createdDate = LocalDateTime.now();
    }

    @JsonIgnore
    public String fullName() {
        if (firstName == null && lastName == null)
            return email;
        return ((lastName != null ? lastName : "") + " " + (firstName != null ? firstName : "")).trim();
    }

    public String getFullName() {
        return fullName();
    }

    // ---- Tên / mã địa bàn (null-safe) ----
    public String provinceId() {
        return province == null ? null : province.getCode();
    }

    public String provinceName() {
        return province == null ? null : province.getName();
    }

    public Long wardId() {
        return ward == null ? null : ward.getId();
    }

    public String wardName() {
        return ward == null ? null : ward.getName();
    }

    /** Địa chỉ đầy đủ = địa chỉ chi tiết + xã + tỉnh (address chỉ giữ phần chi tiết). */
    public String fullAddress() {
        return java.util.stream.Stream.of(address, wardName(), provinceName())
                .filter(s -> s != null && !s.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.joining(", "));
    }
}
