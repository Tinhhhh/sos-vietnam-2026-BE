package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Ai đặt chữ ký ở ô người dân (incident_documents.citizen_sign_channel). */
@Getter
public enum CitizenSignChannel {
    CITIZEN("Người dân tự ký"),
    OFFICER_PROXY("Cán bộ ký hộ (được người dân uỷ quyền)");

    private final String label;

    CitizenSignChannel(String label) {
        this.label = label;
    }
}
