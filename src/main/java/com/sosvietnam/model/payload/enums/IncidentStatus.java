package com.sosvietnam.model.payload.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Vòng đời một ca:
 * PENDING   chưa ai nhận (ca cấp xã đang đếm 15 phút để tự lên tỉnh);
 * VERIFYING cán bộ đã nhận ca, đang gọi / đối chiếu với người báo xem thật giả, tình hình ra sao
 *           (nhả ca thì quay về PENDING; quá 15 phút người dân được yêu cầu chuyển lên tỉnh);
 * RESOLVING cán bộ đã yêu cầu điều động lực lượng, đang xử lý tại hiện trường;
 * RESOLVED  người báo và cán bộ cùng xác nhận đã xử lý xong;
 * CANCELLED người dân huỷ, hoặc hệ thống huỷ ca chờ quá lâu.
 */
@Getter
public enum IncidentStatus {
    PENDING("Chờ tiếp nhận"),
    VERIFYING("Đang xác minh"),
    RESOLVING("Đang điều động xử lý"),
    RESOLVED("Đã hoàn tất"),
    CANCELLED("Đã huỷ");

    /** Ca còn nằm trong hàng đợi (chưa xong, chưa huỷ). */
    public static final Set<IncidentStatus> ACTIVE = EnumSet.of(PENDING, VERIFYING, RESOLVING);
    /** Ca đang có cán bộ giữ. */
    public static final Set<IncidentStatus> HELD = EnumSet.of(VERIFYING, RESOLVING);
    /** Ca đã đóng: nằm trong lịch sử. */
    public static final Set<IncidentStatus> CLOSED = EnumSet.of(RESOLVED, CANCELLED);

    private final String label;

    IncidentStatus(String label) {
        this.label = label;
    }
}
