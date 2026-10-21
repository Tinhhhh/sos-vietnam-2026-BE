package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Vì sao ca cấp xã chuyển lên cấp tỉnh (incidents.escalation_reason). */
@Getter
public enum EscalationReason {
    NO_RESPONSE_TIMEOUT("Quá thời hạn cấp xã chưa có cán bộ tiếp nhận, hệ thống tự chuyển"),
    CITIZEN_REQUEST("Người báo tin yêu cầu chuyển lên cấp tỉnh (quá thời hạn chưa điều động lực lượng)"),
    OFFICER_REQUEST("Cán bộ cấp xã chuyển lên cấp tỉnh");

    private final String label;

    EscalationReason(String label) {
        this.label = label;
    }
}
