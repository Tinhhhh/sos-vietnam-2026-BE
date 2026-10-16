package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Lý do một tin báo SOS bị huỷ. Người dân chọn 4 lý do đầu (không bắt buộc);
 * NO_RESPONDER chỉ hệ thống đặt khi ca chờ quá lâu mà không ai tiếp nhận.
 */
@Getter
public enum CancelReason {
    MISTAKE("Bấm nhầm", true),
    RESOLVED_SELF("Sự việc đã ổn", true),
    CALLED_HOTLINE("Đã gọi 113 / 114", true),
    OTHER("Lý do khác", true),
    NO_RESPONDER("Quá lâu không có cán bộ tiếp nhận, hệ thống tự huỷ", false);

    private final String label;
    /** Người dân được chọn lý do này khi tự huỷ. */
    private final boolean citizenChoice;

    CancelReason(String label, boolean citizenChoice) {
        this.label = label;
        this.citizenChoice = citizenChoice;
    }
}
