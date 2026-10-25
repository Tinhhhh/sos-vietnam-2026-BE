package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Cấp bậc hàm Công an nhân dân (unit_members.rank), từ thấp tới cao. */
@Getter
public enum PoliceRank {
    BINH_NHI("Binh nhì"),
    BINH_NHAT("Binh nhất"),
    HA_SI("Hạ sĩ"),
    TRUNG_SI("Trung sĩ"),
    THUONG_SI("Thượng sĩ"),
    THIEU_UY("Thiếu uý"),
    TRUNG_UY("Trung uý"),
    THUONG_UY("Thượng uý"),
    DAI_UY("Đại uý"),
    THIEU_TA("Thiếu tá"),
    TRUNG_TA("Trung tá"),
    THUONG_TA("Thượng tá"),
    DAI_TA("Đại tá"),
    THIEU_TUONG("Thiếu tướng"),
    TRUNG_TUONG("Trung tướng"),
    THUONG_TUONG("Thượng tướng"),
    DAI_TUONG("Đại tướng");

    private final String label;

    PoliceRank(String label) {
        this.label = label;
    }
}
