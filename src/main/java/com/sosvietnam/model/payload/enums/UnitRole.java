package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Vai trò của cán bộ trong đơn vị (unit_members.unit_role). Trực ban không phải vai trò mà là lượt trực
 * (unit_duty_assignments); chỉ huy luôn có quyền như trực ban (quản lý cán bộ, giao trực ban).
 */
@Getter
public enum UnitRole {
    COMMANDER("Chỉ huy"),
    MEMBER("Cán bộ");

    private final String label;

    UnitRole(String label) {
        this.label = label;
    }
}
