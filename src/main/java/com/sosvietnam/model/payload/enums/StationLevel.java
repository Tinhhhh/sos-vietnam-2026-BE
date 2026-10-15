package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Cấp của trạm cứu hộ (rescue_stations.level), lấy từ field "level" trong data/rescue_stations.json.
 * Khác UnitLevel (cấp của tài khoản) vì có thêm ENTERPRISE: doanh nghiệp cứu hộ giao thông, không thuộc cấp hành chính.
 */
@Getter
public enum StationLevel {
    NATIONAL("Cấp Bộ"),
    PROVINCE("Cấp Tỉnh/TP"),
    WARD("Cấp Xã/Phường"),
    ENTERPRISE("Doanh nghiệp");

    private final String label;

    StationLevel(String label) {
        this.label = label;
    }
}
