package com.sosvietnam.service;

import java.util.Map;

public interface BandoSyncService {
    Map<String, Object> syncFromNationalDatabase();

    /** Bắt đầu tải ranh giới gốc tỉnh / xã từ bando (chạy nền); đang chạy thì chỉ trả trạng thái. */
    Map<String, Object> startBoundarySync();

    Map<String, Object> getStatus();
}
