package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Point;

@Entity
@Table(name = "rescue_stations",
        indexes = @Index(name = "ux_rescue_stations_code", columnList = "code", unique = true))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RescueStation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Mã ổn định lấy từ field "id" trong data/rescue_stations.json (vd: st-catpdanang).
    // DataSeeder upsert theo cột này nên id ở trên không đổi khi nạp lại file.
    @Column(length = 100)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "agency_type", nullable = false, length = 30)
    private AgencyType agencyType;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private StationLevel level;

    @Column(length = 100)
    private String phone;

    // Chỉ huy / trực ban của trạm (vd: "Trung tá Phạm Minh Trí"), null khi file ghi "Đang cập nhật"
    @Column(length = 200)
    private String officer;

    @Column(length = 150)
    private String email;

    @Column(columnDefinition = "TEXT")
    private String services;

    // Loại cơ sở: "Trụ sở chính", "Cơ sở 2", "Đội CSGT địa bàn"...
    @Column(name = "facility_type", length = 100)
    private String facilityType;

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 150)
    private String province;

    @Column(length = 150)
    private String ward;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;
}
