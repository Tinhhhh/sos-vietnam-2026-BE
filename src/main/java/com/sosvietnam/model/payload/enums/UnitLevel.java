package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Cấp của đơn vị mà tài khoản trực ban thuộc về (accounts.unit_level), cũng là cấp đang giữ một sự cố
 * (incidents.routed_level). Tên gọi theo lực lượng:
 * - Công an:      WARD = Công an xã/phường,  PROVINCE = Công an tỉnh/TP
 * - CSGT, PCCC:   WARD = cấp Đội (không trực), PROVINCE = cấp Phòng
 * - Y tế:         chỉ 1 cấp, dùng PROVINCE
 * - NATIONAL: Trung tâm chỉ huy (tài khoản ADMIN)
 * Tài khoản CITIZEN không có cấp (null).
 */
@Getter
public enum UnitLevel {
    NATIONAL("Cấp Bộ"),
    PROVINCE("Cấp Tỉnh/TP"),
    WARD("Cấp Xã/Phường");

    private final String label;

    UnitLevel(String label) {
        this.label = label;
    }
}
