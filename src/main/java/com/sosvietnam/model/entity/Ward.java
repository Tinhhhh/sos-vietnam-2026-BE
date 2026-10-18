package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;
import java.time.LocalDateTime;

@Entity
@Table(name = "wards", indexes = {
    @Index(name = "idx_wards_province", columnList = "province_id"),
    @Index(name = "idx_ward_malk", columnList = "malk")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Ward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 50)
    private String malk;

    @Column(nullable = false, length = 150)
    private String name;

    // 1 xã thuộc đúng 1 tỉnh; 1 tỉnh có nhiều xã
    @ManyToOne
    @JoinColumn(name = "province_id", nullable = false)
    private Province province;

    @Column(name = "sap_nhap_tu", columnDefinition = "TEXT")
    private String sapNhapTu;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Geometry, 4326)")
    private Geometry geometry;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
