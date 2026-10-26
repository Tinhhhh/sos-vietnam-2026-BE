package com.sosvietnam.service;

/**
 * Tự tính lại tuyến khi xe đi lệch (kiểu Grab): mỗi điểm GPS của lượt đang trên đường, lệch tuyến hiện hành quá
 * {@code app.routing.off-route-m} ở 2 điểm liên tiếp thì tính tuyến mới từ vị trí xe tới điểm đến (tuyến cũ giữ lại,
 * superseded_at), báo INCIDENT_UPDATED để màn theo dõi vẽ lại. Chạy nền, không chặn luồng nhận điểm Traccar.
 */
public interface RouteRecalcService {

    void onPosition(Long dispatchId, double lat, double lng);
}
