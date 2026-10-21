package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Lý do một lượt giữ ca (incident_claims) kết thúc. */
@Getter
public enum ClaimEndReason {
    RELEASED("Trả về hàng đợi chung"),
    HANDED_OVER("Giao cho cán bộ khác"),
    ESCALATED("Chuyển lên cấp tỉnh"),
    CLOSED("Ca đã kết thúc"),
    AUTO_RELEASED("Tự trả về hàng đợi (cán bộ mất kết nối)"),
    TRANSFERRED("Chuyển sang lực lượng khác");

    private final String label;

    ClaimEndReason(String label) {
        this.label = label;
    }
}
