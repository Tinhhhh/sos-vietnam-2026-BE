package com.sosvietnam.model.payload.enums;

/**
 * Điểm đến (hiện trường): GPS người dân gửi, cán bộ sửa (GPS lệch / dân cho thêm địa chỉ), hay điểm xe dừng ở đường lớn
 * gần nhất (dân trong hẻm, xe khó vào: GET /geo/road-snap, 25/10/2026). Cột varchar, thêm giá trị không cần migration.
 */
public enum DestinationSource {
    CITIZEN_GPS,
    OFFICER_EDIT,
    ROAD_SNAP
}
