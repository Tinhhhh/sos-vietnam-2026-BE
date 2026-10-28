package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Ai gửi tin báo: chính người gặp sự cố (SELF) hay người khác báo hộ (PROXY).
 * PROXY chưa mở: FE để nút mờ, BE trả 400 nếu gửi lên.
 */
@Getter
public enum ReportType {
    SELF("Tự báo", true),
    PROXY("Báo hộ", false);

    private final String label;
    /** Người dân chọn được loại này lúc gửi SOS. */
    private final boolean available;

    ReportType(String label, boolean available) {
        this.label = label;
        this.available = available;
    }
}
