package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.DemoSettingsRequest;
import com.sosvietnam.model.payload.response.DemoSettingsResponse;

/** Công tắc ca demo dùng chung cho mọi tài khoản (bảng app_settings). */
public interface DemoModeService {

    /** DemoIncidentSeeder có sinh ca mới không. */
    boolean generateEnabled();

    /** Ca [DEMO] có bị ẩn khỏi hàng đợi / lịch sử / tin STOMP của cán bộ không. */
    boolean hidden();

    /** Tốc độ xe giả của "Mô phỏng" (km/h); chưa đổi ở giao diện = app.traccar.simulation.speed-kmh. */
    double simulationSpeedKmh();

    DemoSettingsResponse get();

    DemoSettingsResponse update(DemoSettingsRequest request);

    /** Xoá hẳn ca [DEMO]: OPEN = ca đang mở, ALL = mọi ca demo. Hàng đợi đang mở gỡ thẻ ngay. */
    com.sosvietnam.model.payload.response.DemoPurgeResponse purge(com.sosvietnam.model.payload.enums.DemoPurgeScope scope);
}
