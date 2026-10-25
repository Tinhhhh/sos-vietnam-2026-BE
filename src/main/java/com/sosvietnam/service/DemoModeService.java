package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.DemoSettingsRequest;
import com.sosvietnam.model.payload.response.DemoSettingsResponse;

/** Công tắc ca demo dùng chung cho mọi tài khoản (bảng app_settings). */
public interface DemoModeService {

    /** DemoIncidentSeeder có sinh ca mới không. */
    boolean generateEnabled();

    /** Ca [DEMO] có bị ẩn khỏi hàng đợi / lịch sử / tin STOMP của cán bộ không. */
    boolean hidden();

    DemoSettingsResponse get();

    DemoSettingsResponse update(DemoSettingsRequest request);
}
