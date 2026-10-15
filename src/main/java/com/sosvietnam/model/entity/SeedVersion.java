package com.sosvietnam.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Phiên bản của từng bộ dữ liệu nạp vào DB (bảng seed_versions, tạo trong schema.sql):
 * - rescue_stations: DataSeeder ghi khi data/rescue_stations.json đổi checksum.
 * - boundaries: BandoSyncService ghi sau mỗi lần đối soát với bando.com.vn.
 * applied_at = lần dữ liệu thật sự đổi, checked_at = lần kiểm tra gần nhất.
 */
@Entity
@Table(name = "seed_versions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SeedVersion {

    public static final String RESCUE_STATIONS = "rescue_stations";
    public static final String BOUNDARIES = "boundaries";

    private static final DateTimeFormatter VERSION_FORMAT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Id
    @Column(length = 100)
    private String name;

    // SHA-256 của nội dung nguồn, dùng để biết dữ liệu có đổi không
    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(length = 50)
    private String version;

    @Column(name = "applied_at", nullable = false)
    private Instant appliedAt;

    @Column(name = "checked_at")
    private Instant checkedAt;

    /** Nhãn phiên bản mặc định theo ngày (giờ VN), vd "2026.10.15". */
    public static String versionOf(Instant time) {
        return VERSION_FORMAT.format(time);
    }
}
