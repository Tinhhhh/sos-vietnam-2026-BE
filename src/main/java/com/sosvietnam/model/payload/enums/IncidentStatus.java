package com.sosvietnam.model.payload.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Trạng thái sự cố. PENDING = chưa ai nhận (đang đếm giờ leo thang nếu ở cấp xã);
 * ACCEPTED = đã có trực ban nhận và đang khoá ca; nhả ca thì quay về PENDING.
 */
@Getter
public enum IncidentStatus {
    PENDING("Chờ tiếp nhận"),
    ACCEPTED("Đã tiếp nhận"),
    VERIFYING("Đang xác minh"),
    DISPATCHED("Đang điều phối"),
    RESOLVED("Đã hoàn tất"),
    CANCELLED("Đã huỷ");

    /** Ca còn nằm trong hàng đợi (chưa xong, chưa huỷ). */
    public static final Set<IncidentStatus> ACTIVE = EnumSet.of(PENDING, ACCEPTED, VERIFYING, DISPATCHED);

    private final String label;

    IncidentStatus(String label) {
        this.label = label;
    }
}
