package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Cách ký trên văn bản báo cáo (incident_documents.*_sign_type). */
@Getter
public enum SignatureType {
    DRAW("Vẽ tay"),
    TYPE("Gõ họ tên");

    private final String label;

    SignatureType(String label) {
        this.label = label;
    }
}
