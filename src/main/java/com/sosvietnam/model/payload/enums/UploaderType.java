package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Ai đưa file lên: người báo tin (không có tài khoản) hay cán bộ. */
@Getter
public enum UploaderType {
    CITIZEN("Người báo tin"),
    OFFICER("Cán bộ");

    private final String label;

    UploaderType(String label) {
        this.label = label;
    }
}
