package com.sosvietnam.model.payload.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Cách lượt điều động được xác nhận "Đã đến" (incident_dispatches.arrival_source). */
@Getter
@RequiredArgsConstructor
public enum ArrivalSource {
    GPS("Theo GPS (trong bán kính điểm đến)"),
    HOLDER_OVERRIDE("Người giữ ca xác nhận (không theo GPS)"),
    CITIZEN("Người báo tin xác nhận");

    private final String label;
}
