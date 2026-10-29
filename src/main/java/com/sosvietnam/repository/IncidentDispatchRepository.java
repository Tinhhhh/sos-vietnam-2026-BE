package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentDispatchRepository extends JpaRepository<IncidentDispatch, Long> {

    List<IncidentDispatch> findByIncident_IdOrderByDispatchedAtAsc(String incidentId);

    long countByIncident_Id(String incidentId);

    boolean existsByIncident_IdAndStation_Id(String incidentId, Long stationId);

    /** Lượt chủ trì mới nhất của ca (kể cả lượt bị từ chối, để bên giao thấy lý do). */
    Optional<IncidentDispatch> findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(String incidentId, DispatchRole role);

    /** Lượt chủ trì của nhiều ca (thẻ hàng đợi hiện trạng thái uỷ quyền). */
    List<IncidentDispatch> findByIncident_IdInAndRole(java.util.Collection<String> incidentIds, DispatchRole role);

    /** Mã mọi lượt điều động của các ca (dừng mô phỏng trước khi xoá ca). */
    @org.springframework.data.jpa.repository.Query("SELECT d.id FROM IncidentDispatch d WHERE d.incident.id IN :incidentIds")
    List<Long> findIdsByIncidentIds(@org.springframework.data.repository.query.Param("incidentIds") java.util.Collection<String> incidentIds);

    /** Ca đang được uỷ quyền cho một đơn vị cấp xã (lực lượng + xã của trạm), để đưa vào hàng đợi của xã. */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM IncidentDispatch d JOIN FETCH d.incident i
            WHERE d.role = com.sosvietnam.model.payload.enums.DispatchRole.PRIMARY
              AND d.assignedTo IS NOT NULL
              AND d.status IN :statuses
              AND d.station.agencyType = :agency
              AND d.station.ward.id = :wardId
              AND i.status IN :incidentStatuses
            """)
    List<IncidentDispatch> findDelegatedToWard(
            @org.springframework.data.repository.query.Param("agency") com.sosvietnam.model.payload.enums.AgencyType agency,
            @org.springframework.data.repository.query.Param("wardId") Long wardId,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<DispatchStatus> statuses,
            @org.springframework.data.repository.query.Param("incidentStatuses") java.util.Collection<com.sosvietnam.model.payload.enums.IncidentStatus> incidentStatuses);

    /**
     * Lượt điều động đang giao cho các cán bộ này ở những ca CÒN MỞ (ca đã huỷ / xong thì không tính,
     * dù lượt điều động của ca đó vẫn giữ trạng thái cũ).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM IncidentDispatch d JOIN FETCH d.incident i
            WHERE d.assignedTo.id IN :accountIds
              AND d.status IN :statuses
              AND i.status IN :incidentStatuses
            """)
    List<IncidentDispatch> findActiveAssignments(
            @org.springframework.data.repository.query.Param("accountIds") java.util.Collection<java.util.UUID> accountIds,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<DispatchStatus> statuses,
            @org.springframework.data.repository.query.Param("incidentStatuses") java.util.Collection<com.sosvietnam.model.payload.enums.IncidentStatus> incidentStatuses);

    /**
     * Lượt chủ trì đang trên đường (EN_ROUTE) mà người phụ trách hiện trường có số hiệu này.
     * Mới xuất phát nhất trước (số hiệu trùng ở 2 lượt: lấy lượt đầu).
     */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM IncidentDispatch d JOIN d.fieldMember f
            WHERE d.role = com.sosvietnam.model.payload.enums.DispatchRole.PRIMARY
              AND d.status = com.sosvietnam.model.payload.enums.DispatchStatus.EN_ROUTE
              AND f.badgeNumber = :badge
            ORDER BY d.departedAt DESC, d.id DESC
            """)
    List<IncidentDispatch> findEnRouteByTrackerBadge(
            @org.springframework.data.repository.query.Param("badge") String badge);

    /** Lượt các cán bộ này đang phụ trách hiện trường ở những ca còn mở (một người chỉ đi một lượt). */
    @org.springframework.data.jpa.repository.Query("""
            SELECT d FROM IncidentDispatch d JOIN FETCH d.incident i
            WHERE d.fieldMember.id IN :memberIds
              AND d.status IN :statuses
              AND i.status IN :incidentStatuses
            """)
    List<IncidentDispatch> findActiveFieldTrips(
            @org.springframework.data.repository.query.Param("memberIds") java.util.Collection<java.util.UUID> memberIds,
            @org.springframework.data.repository.query.Param("statuses") java.util.Collection<DispatchStatus> statuses,
            @org.springframework.data.repository.query.Param("incidentStatuses") java.util.Collection<com.sosvietnam.model.payload.enums.IncidentStatus> incidentStatuses);

    /**
     * Lượt điều động của link người phụ trách hiện trường (theo SHA-256 của mã), kèm ca: STOMP CONNECT và gọi video
     * đọc trạng thái ca ngoài transaction.
     */
    @org.springframework.data.jpa.repository.Query(
            "SELECT d FROM IncidentDispatch d JOIN FETCH d.incident WHERE d.fieldTokenHash = :hash")
    Optional<IncidentDispatch> findByFieldTokenHash(@org.springframework.data.repository.query.Param("hash") String fieldTokenHash);

    /** Lượt chủ trì đang hoạt động của ca (không tính lượt đã bị từ chối). */
    Optional<IncidentDispatch> findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(
            String incidentId, DispatchRole role, DispatchStatus status);

    /**
     * Ca bị huỷ: đóng mọi lượt chưa xong (REJECTED + lý do; link người đi hết hiệu lực). Lượt đã xong / đã đóng giữ nguyên.
     * @return số lượt vừa đóng
     */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("""
            UPDATE IncidentDispatch d
            SET d.status = com.sosvietnam.model.payload.enums.DispatchStatus.REJECTED, d.rejectReason = :reason,
                d.fieldTokenHash = null, d.fieldTokenIssuedAt = null, d.updatedAt = :now
            WHERE d.incident.id = :incidentId
              AND d.status IN (com.sosvietnam.model.payload.enums.DispatchStatus.PENDING_ACCEPT,
                               com.sosvietnam.model.payload.enums.DispatchStatus.ACCEPTED,
                               com.sosvietnam.model.payload.enums.DispatchStatus.EN_ROUTE,
                               com.sosvietnam.model.payload.enums.DispatchStatus.ARRIVED)
            """)
    int closeOpenForIncident(@org.springframework.data.repository.query.Param("incidentId") String incidentId,
                             @org.springframework.data.repository.query.Param("reason") String reason,
                             @org.springframework.data.repository.query.Param("now") LocalDateTime now);
}
