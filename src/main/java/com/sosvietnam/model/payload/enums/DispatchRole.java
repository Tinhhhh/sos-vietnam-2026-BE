package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Vai trò của một lượt điều động trong ca (incident_dispatches.role). */
@Getter
public enum DispatchRole {
    PRIMARY("Đơn vị chủ trì"),
    SUPPORT("Đơn vị hỗ trợ");

    private final String label;

    DispatchRole(String label) {
        this.label = label;
    }
}
