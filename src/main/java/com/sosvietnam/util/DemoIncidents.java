package com.sosvietnam.util;

import com.sosvietnam.model.entity.Incident;

import java.util.Set;

/** Nhận ra ca test do DemoIncidentSeeder (hoặc script demo cũ) tạo: họ tên người báo bắt đầu bằng "[DEMO] ". */
public final class DemoIncidents {

    public static final String MARK = "[DEMO] ";
    // Tên các ca test do script local-db/demo-incidents.mjs cũ tạo (không có MARK)
    public static final Set<String> LEGACY_NAMES =
            Set.of("Trần Văn Demo (tỉnh)", "Nguyễn Thị Demo (xã)", "Lê Văn Demo (CSGT)");

    private DemoIncidents() {
    }

    public static boolean isDemo(String citizenName) {
        return citizenName != null && (citizenName.startsWith(MARK) || LEGACY_NAMES.contains(citizenName));
    }

    public static boolean isDemo(Incident incident) {
        return incident != null && isDemo(incident.getCitizenName());
    }
}
