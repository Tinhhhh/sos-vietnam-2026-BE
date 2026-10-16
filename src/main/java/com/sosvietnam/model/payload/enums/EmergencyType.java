package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Loại sự cố người dân chọn khi gửi SOS, kèm lực lượng nhận ca. */
@Getter
public enum EmergencyType {
    POLICE("An ninh trật tự", AgencyType.POLICE),
    FIRE("Cháy nổ / Cứu nạn cứu hộ", AgencyType.FIRE),
    // Y tế không nhận ca qua SOS: người dân gọi 115 / bệnh viện gần nhất trong danh bạ
    AMBULANCE("Cấp cứu y tế", AgencyType.HOSPITAL),
    TRAFFIC("Tai nạn giao thông", AgencyType.CSGT);

    private final String label;
    private final AgencyType agency;

    EmergencyType(String label, AgencyType agency) {
        this.label = label;
        this.agency = agency;
    }
}
