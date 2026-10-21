package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Kết quả ghi trong văn bản kết thúc ca (incident_reports.result). */
@Getter
public enum ReportResult {
    RESOLVED("Đã xử lý xong"),
    FALSE_ALARM("Báo khống"),
    FORCED("Kết thúc bắt buộc");

    private final String label;

    ReportResult(String label) {
        this.label = label;
    }
}
