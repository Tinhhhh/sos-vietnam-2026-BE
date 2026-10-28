package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Lý do một tin báo SOS bị huỷ. Người dân chọn 4 lý do đầu (không bắt buộc);
 * NO_RESPONDER chỉ hệ thống đặt khi ca chờ quá lâu mà không ai tiếp nhận; OFFICER_CANCELLED khi cán bộ giữ ca huỷ
 * (lý do ghi ở incident_claims.note).
 */
@Getter
public enum CancelReason {
    MISTAKE("Bấm nhầm", true),
    RESOLVED_SELF("Sự việc đã ổn", true),
    CALLED_HOTLINE("Đã gọi 113 / 114", true),
    OTHER("Lý do khác", true),
    NO_RESPONDER("Quá lâu không có cán bộ tiếp nhận, hệ thống tự huỷ", false),
    OFFICER_CANCELLED("Cán bộ huỷ ca", false),
    /** Cán bộ giữ ca kết luận báo khống (tính vào khoá SĐT). */
    FALSE_ALARM("Cán bộ kết luận báo khống", false);

    private final String label;
    /** Người dân được chọn lý do này khi tự huỷ. */
    private final boolean citizenChoice;

    CancelReason(String label, boolean citizenChoice) {
        this.label = label;
        this.citizenChoice = citizenChoice;
    }
}
