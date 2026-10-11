package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;
import java.time.LocalDateTime;

@Entity
@Table(name = "wards", indexes = {
    @Index(name = "idx_ward_province", columnList = "province_code"),
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

    @Column(name = "province_code", length = 20)
    private String provinceCode;

    @Column(name = "province_name", length = 150)
    private String provinceName;

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
