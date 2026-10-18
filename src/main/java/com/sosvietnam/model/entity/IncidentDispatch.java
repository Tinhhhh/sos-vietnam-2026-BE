package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.UnitLevel;
import jakarta.persistence.*;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Lượt điều động: một trạm / lực lượng (rescue_stations) được điều tới tham gia xử lý một ca.
 * Quan hệ n-n: 1 ca có nhiều trạm (vd xã điều rồi tỉnh điều thêm khi chuyển lên tỉnh), 1 trạm tham gia nhiều ca.
 * Khoá chính là cặp (incident_id, station_id).
 */
@Entity
@Table(name = "incident_dispatches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentDispatch {

    @EmbeddedId
    private Key id;

    @MapsId("incidentId")
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id")
    private Incident incident;

    @MapsId("stationId")
    @ManyToOne(optional = false)
    @JoinColumn(name = "station_id")
    private RescueStation station;

    // Cán bộ ra lệnh điều động; null với dữ liệu cũ
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispatched_by")
    private Account dispatchedBy;

    // Cấp ra lệnh (xã / tỉnh)
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_level", length = 20)
    private UnitLevel unitLevel;

    @Column(length = 500)
    private String note;

    @Column(name = "dispatched_at", nullable = false)
    private LocalDateTime dispatchedAt;

    @Embeddable
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Key implements Serializable {
        @Column(name = "incident_id", length = 50)
        private String incidentId;

        @Column(name = "station_id")
        private Long stationId;

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(incidentId, k.incidentId) && Objects.equals(stationId, k.stationId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(incidentId, stationId);
        }
    }
}
