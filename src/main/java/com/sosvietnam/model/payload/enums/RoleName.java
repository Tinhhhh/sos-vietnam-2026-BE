package com.sosvietnam.model.payload.enums;

import lombok.Getter;

import java.util.Arrays;
import java.util.Optional;

/**
 * Quyền của tài khoản. Bảng roles lưu role_name dạng chữ (= getRole()), Spring Security
 * dùng đúng chuỗi đó làm authority (hasAuthority("ADMIN")...). Thứ tự khai báo = thứ tự hiển thị.
 */
@Getter
public enum RoleName {
    ADMIN("ADMIN", "Quản trị hệ thống / Trung tâm chỉ huy"),
    OFFICER("OFFICER", "Cán bộ"),
    CITIZEN("CITIZEN", "Người dân");

    private final String role;
    private final String description;

    RoleName(String role, String description) {
        this.role = role;
        this.description = description;
    }

    public static Optional<RoleName> fromRole(String role) {
        return Arrays.stream(values()).filter(r -> r.role.equalsIgnoreCase(role)).findFirst();
    }

    public static boolean isExistRole(String role) {
        return fromRole(role).isPresent();
    }

    /** Tài khoản nội bộ (ADMIN, OFFICER), khác người dân. */
    public boolean isStaff() {
        return this != CITIZEN;
    }
}
