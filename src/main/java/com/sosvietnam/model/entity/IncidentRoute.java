package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.DestinationSource;
import com.sosvietnam.model.payload.enums.RouteOriginSource;
import com.sosvietnam.model.payload.enums.RouteProvider;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Tuyến đường của một lượt điều động: trạm → hiện trường. Không ghi đè: tính lại (đổi trạm / sửa điểm đến)
 * thì đặt superseded_at cho dòng cũ và thêm dòng mới, để biên bản còn biết lúc đầu ước tính bao lâu.
 */
@Entity
@Table(name = "incident_routes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentRoute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "dispatch_id", nullable = false)
    private IncidentDispatch dispatch;

    @Column(name = "incident_id", length = 50, nullable = false)
    private String incidentId;

    @Column(name = "station_id")
    private Long stationId;

    @Column(name = "origin_lat", nullable = false)
    private Double originLat;

    @Column(name = "origin_lng", nullable = false)
    private Double originLng;

    @Column(name = "origin_address", length = 500)
    private String originAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin_source", length = 20, nullable = false)
    private RouteOriginSource originSource;

    @Column(name = "dest_lat", nullable = false)
    private Double destLat;

    @Column(name = "dest_lng", nullable = false)
    private Double destLng;

    @Column(name = "dest_address", length = 500)
    private String destAddress;

    @Enumerated(EnumType.STRING)
    @Column(name = "dest_source", length = 20, nullable = false)
    private DestinationSource destSource;

    @Column(name = "distance_m")
    private Integer distanceM;

    @Column(name = "duration_s")
    private Integer durationS;

    @Column(name = "duration_traffic_s")
    private Integer durationTrafficS;

    // Encoded polyline (định dạng Google, độ chính xác 1e-5)
    @Column(name = "polyline", columnDefinition = "text")
    private String polyline;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", length = 20)
    private RouteProvider provider;

    @Column(name = "computed_by")
    private UUID computedBy;

    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;
}
