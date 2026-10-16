package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Lý do một lượt giữ ca (incident_claims) kết thúc. */
@Getter
public enum ClaimEndReason {
    RELEASED("Trả về hàng đợi chung"),
    HANDED_OVER("Giao cho cán bộ khác"),
    CLOSED("Ca đã kết thúc");

    private final String label;

    ClaimEndReason(String label) {
        this.label = label;
    }
}
