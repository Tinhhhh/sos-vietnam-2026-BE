package com.sosvietnam.util;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * Tên tỉnh / xã theo khoá, đọc 1 lần (không kèm geometry) rồi giữ trong bộ nhớ.
 * Dùng khi trả danh sách lớn (trạm cứu hộ): nạp entity Ward / Province để lấy tên sẽ kéo cả
 * polygon ranh giới và tạo 1 câu SELECT cho mỗi xã. Tên địa bàn hầu như không đổi; làm mới sau TTL.
 */
@Component
@RequiredArgsConstructor
public class AreaNameCache {

    private static final long TTL_MS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbcTemplate;

    private volatile Snapshot snapshot;

    private record Snapshot(Map<String, String> provinces, Map<Long, String> wards, long loadedAt) {
    }

    public String provinceName(String provinceId) {
        return provinceId == null ? null : current().provinces().get(provinceId);
    }

    public String wardName(Long wardId) {
        return wardId == null ? null : current().wards().get(wardId);
    }

    private Snapshot current() {
        Snapshot s = snapshot;
        if (s == null || System.currentTimeMillis() - s.loadedAt() > TTL_MS) {
            s = load();
            snapshot = s;
        }
        return s;
    }

    private Snapshot load() {
        Map<String, String> provinces = new HashMap<>();
        jdbcTemplate.query("SELECT code, name FROM provinces", rs -> {
            provinces.put(rs.getString("code"), rs.getString("name"));
        });
        Map<Long, String> wards = new HashMap<>();
        jdbcTemplate.query("SELECT id, name FROM wards", rs -> {
            wards.put(rs.getLong("id"), rs.getString("name"));
        });
        return new Snapshot(Map.copyOf(provinces), Map.copyOf(wards), System.currentTimeMillis());
    }
}
