package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Vì sao một SĐT bị khoá gửi SOS (bảng phone_locks). */
@Getter
public enum PhoneLockReason {
    FALSE_ALARM("Báo tin giả: cán bộ kết luận báo khống 3 lần trong 24 giờ"),
    MISTAKE_CANCEL("Huỷ vì bấm nhầm 4 lần trong 24 giờ");

    private final String label;

    PhoneLockReason(String label) {
        this.label = label;
    }
}
