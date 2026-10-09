package com.sosvietnam.service;

import java.util.Map;

public interface BandoSyncService {
    Map<String, Object> syncFromNationalDatabase();

    Map<String, Object> getStatus();
}
