package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Loại tin realtime về một sự cố gửi cho trực ban. */
@Getter
public enum IncidentEvent {
    INCIDENT_NEW("Có sự cố mới"),
    INCIDENT_UPDATED("Sự cố thay đổi (nhận, nhả, giao ca...)"),
    INCIDENT_ESCALATED("Sự cố leo thang lên cấp tỉnh"),
    INCIDENT_REMOVED("Sự cố rời hàng đợi (hoàn tất, huỷ)");

    private final String label;

    IncidentEvent(String label) {
        this.label = label;
    }
}
