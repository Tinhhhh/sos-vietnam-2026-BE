package com.sosvietnam.service;

import java.util.List;

/**
 * Tuyến đường xe ô tô qua các điểm [lat, lng] theo thứ tự, tính ở BE (hiện là OSRM, máy chủ
 * {@code app.routing.osrm-url}). Dùng cho tính lại tuyến khi xe lệch đường và nút "Rẽ đường khác" của mô phỏng.
 */
public interface RoutingService {

    /** polyline = định dạng Google 1e-5 (như incident_routes.polyline); path = polyline đã giải mã [lat, lng]. */
    record RoadRoute(String polyline, List<double[]> path, int distanceM, int durationS) {
    }

    /** null khi máy chủ chỉ đường lỗi / quá hạn / không có đường. */
    RoadRoute route(List<double[]> points);
}
