package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;
import org.locationtech.jts.geom.Geometry;

@Entity
@Table(name = "provinces")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Province {

    @Id
    @Column(length = 20)
    private String code;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(name = "name_en", length = 150)
    private String nameEn;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @JsonIgnore
    @Column(columnDefinition = "geometry(Geometry, 4326)")
    private Geometry geometry;
}
