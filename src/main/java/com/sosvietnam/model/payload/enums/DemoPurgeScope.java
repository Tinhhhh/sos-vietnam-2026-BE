package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Phạm vi nút "Dọn ca [DEMO]" ở Cài đặt › Ca demo. */
@Getter
public enum DemoPurgeScope {
    OPEN("Ca [DEMO] đang mở (chờ nhận, đang xác minh, đang điều động)"),
    ALL("Mọi ca [DEMO], kể cả ca đã đóng");

    private final String label;

    DemoPurgeScope(String label) {
        this.label = label;
    }
}
