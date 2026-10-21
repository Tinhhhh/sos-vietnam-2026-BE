package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Vòng đời một lượt điều động (incident_dispatches.status) và bước tương ứng trên stepper
 * Điều động (1) → Uỷ quyền (2) → Theo dõi (3) → Kết thúc (4).
 */
@Getter
public enum DispatchStatus {
    PENDING_ACCEPT("Chờ đơn vị được uỷ quyền nhận", 2),
    ACCEPTED("Đơn vị đã nhận, chưa xuất phát", 2),
    REJECTED("Đơn vị từ chối", 1),
    EN_ROUTE("Đang di chuyển tới hiện trường", 3),
    ARRIVED("Đã tới hiện trường, đang xử lý", 4),
    DONE("Đã xử lý xong", 4);

    /** Lượt còn liên quan tới đơn vị được uỷ quyền (chưa từ chối, chưa xong) */
    public static final java.util.Set<DispatchStatus> DELEGATED =
            java.util.EnumSet.of(PENDING_ACCEPT, ACCEPTED, EN_ROUTE, ARRIVED);

    private final String label;
    private final int step;

    DispatchStatus(String label, int step) {
        this.label = label;
        this.step = step;
    }
}
