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
 * Tuyến đường của lượt ở incident_routes (vị trí xe sẽ lấy từ Traccar, không lưu ở DB).
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
    private UnitMember dispatchedBy;

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
    private UnitMember assignedTo;

    // Người phụ trách hiện trường (cầm điện thoại GPS, số hiệu = mã thiết bị Traccar): người nhận ca của
    // đơn vị đi chọn lúc xuất phát; null khi chưa xuất phát
    @ManyToOne
    @JoinColumn(name = "field_member_id")
    private UnitMember fieldMember;

    // Link của người phụ trách hiện trường (/f/{token}): chỉ lưu SHA-256; null = chưa cấp / đã huỷ (đổi người)
    @Column(name = "field_token_hash", length = 64)
    private String fieldTokenHash;

    @Column(name = "field_token_issued_at")
    private LocalDateTime fieldTokenIssuedAt;

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

    // "Đã đến" xác nhận thế nào: GPS trong bán kính / người giữ ca kèm lý do / người báo tin (V17)
    @Enumerated(EnumType.STRING)
    @Column(name = "arrival_source", length = 20)
    private com.sosvietnam.model.payload.enums.ArrivalSource arrivalSource;

    @Column(name = "arrival_note", length = 255)
    private String arrivalNote;

    @ManyToOne
    @JoinColumn(name = "arrival_by_member_id")
    private UnitMember arrivalBy;

    // khoảng cách GPS tới điểm đến lúc xác nhận (m); null = không có GPS
    @Column(name = "arrival_distance_m")
    private Integer arrivalDistanceM;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Đã chọn người phụ trách hiện trường nhưng chưa bấm "Khởi hành". */
    public boolean awaitingDeparture() {
        return status == com.sosvietnam.model.payload.enums.DispatchStatus.ACCEPTED && fieldMember != null;
    }

    /** Nhãn trạng thái cho giao diện ("Chờ khởi hành" khi đã chọn người mà chưa đi). */
    public String statusLabel() {
        return awaitingDeparture() ? "Đã chọn người đi, chờ khởi hành" : status.getLabel();
    }
}
