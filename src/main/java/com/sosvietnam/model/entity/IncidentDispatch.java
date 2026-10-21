package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Lượt điều động: một trạm / lực lượng (rescue_stations) được cử tới xử lý một ca.
 * Mỗi ca có tối đa 1 lượt PRIMARY đang hoạt động (index ux_incident_dispatches_primary), thêm lượt SUPPORT nếu cần.
 * Tuyến đường của lượt ở incident_routes, vị trí xe ở incident_route_positions.
 */
@Entity
@Table(name = "incident_dispatches")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentDispatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(optional = false)
    @JoinColumn(name = "station_id", nullable = false)
    private RescueStation station;

    // Cán bộ ra lệnh điều động; null với dữ liệu cũ
    // EAGER: thẻ hàng đợi (đẩy qua STOMP ngoài transaction) cần tên người giao / người nhận
    @ManyToOne
    @JoinColumn(name = "dispatched_by")
    private Account dispatchedBy;

    // Cấp của người ra lệnh (bên giao việc)
    @Enumerated(EnumType.STRING)
    @Column(name = "unit_level", length = 20)
    private UnitLevel unitLevel;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", length = 20, nullable = false)
    private DispatchRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private DispatchStatus status;

    // Cán bộ của đơn vị được uỷ quyền (bên nhận việc); null = chính đơn vị ra lệnh đi
    @ManyToOne
    @JoinColumn(name = "assigned_to")
    private Account assignedTo;

    @Column(length = 500)
    private String note;

    @Column(name = "dispatched_at", nullable = false)
    private LocalDateTime dispatchedAt;

    @Column(name = "responded_at")
    private LocalDateTime respondedAt;

    @Column(name = "reject_reason", length = 255)
    private String rejectReason;

    @Column(name = "departed_at")
    private LocalDateTime departedAt;

    @Column(name = "arrived_at")
    private LocalDateTime arrivedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
