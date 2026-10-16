package com.sosvietnam.repository;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Các lệnh nhận / nhả / giao / leo thang là UPDATE có điều kiện: DB tự đảm bảo chỉ một
 * thao tác thắng khi hai trực ban bấm cùng lúc (giống giữ vé), kết quả 0 dòng = đã có người khác làm trước.
 */
@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    List<Incident> findByStatusInAndRoutedAgencyInOrderByCreatedAtDesc(
            Collection<IncidentStatus> statuses, Collection<AgencyType> agencies);

    /** Ca còn mở chưa được phân luồng (tạo bởi BE bản cũ, chưa có routed_agency). */
    List<Incident> findByStatusInAndRoutedAgencyIsNull(Collection<IncidentStatus> statuses);

    boolean existsByHandlerIdAndStatusIn(UUID handlerId, Collection<IncidentStatus> statuses);

    List<Incident> findByHandlerIdInAndStatusIn(Collection<UUID> handlerIds, Collection<IncidentStatus> statuses);

    /** Ca Công an cấp xã còn chờ, chưa leo thang: hẹn giờ lại khi BE khởi động. */
    @Query("""
        SELECT i FROM Incident i
        WHERE i.status = com.sosvietnam.model.payload.enums.IncidentStatus.PENDING
          AND i.routedLevel = com.sosvietnam.model.payload.enums.UnitLevel.WARD
          AND i.escalateAt IS NOT NULL AND i.escalatedAt IS NULL
        """)
    List<Incident> findAwaitingEscalation();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Incident i SET i.handlerId = :handlerId, i.claimedAt = :now,
               i.acceptedAt = COALESCE(i.acceptedAt, :now),
               i.status = com.sosvietnam.model.payload.enums.IncidentStatus.ACCEPTED, i.updatedAt = :now
        WHERE i.id = :id AND i.handlerId IS NULL
          AND i.status = com.sosvietnam.model.payload.enums.IncidentStatus.PENDING
          AND i.routedLevel = :level
        """)
    int claim(@Param("id") String id, @Param("handlerId") UUID handlerId, @Param("level") UnitLevel level,
              @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Incident i SET i.handlerId = NULL, i.claimedAt = NULL,
               i.status = com.sosvietnam.model.payload.enums.IncidentStatus.PENDING, i.updatedAt = :now
        WHERE i.id = :id AND i.handlerId = :handlerId
          AND i.status = com.sosvietnam.model.payload.enums.IncidentStatus.ACCEPTED
        """)
    int release(@Param("id") String id, @Param("handlerId") UUID handlerId, @Param("now") LocalDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Incident i SET i.handlerId = :toId, i.claimedAt = :now, i.updatedAt = :now
        WHERE i.id = :id AND i.handlerId = :fromId
          AND i.status = com.sosvietnam.model.payload.enums.IncidentStatus.ACCEPTED
        """)
    int handOver(@Param("id") String id, @Param("fromId") UUID fromId, @Param("toId") UUID toId,
                 @Param("now") LocalDateTime now);

    /** Huỷ khi ca còn mở; 0 dòng = ca đã đóng / đã huỷ trước đó. Tự mở transaction nếu chưa có (job lúc khởi động). */
    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Incident i SET i.status = com.sosvietnam.model.payload.enums.IncidentStatus.CANCELLED,
               i.cancelledAt = :now, i.cancelReason = :reason, i.updatedAt = :now
        WHERE i.id = :id AND i.status IN :statuses
        """)
    int cancel(@Param("id") String id, @Param("reason") CancelReason reason,
               @Param("statuses") Collection<IncidentStatus> statuses, @Param("now") LocalDateTime now);

    /** Ca chưa ai nhận, tạo trước mốc `before` (để hệ thống tự huỷ). */
    List<Incident> findByStatusAndHandlerIdIsNullAndCreatedAtBefore(IncidentStatus status, LocalDateTime before);

    /** Chỉ leo thang khi ca vẫn chưa ai nhận và đã quá hạn: người vừa nhận ca thì lệnh này không làm gì. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Incident i SET i.routedLevel = com.sosvietnam.model.payload.enums.UnitLevel.PROVINCE,
               i.escalatedAt = :now, i.updatedAt = :now
        WHERE i.id = :id AND i.handlerId IS NULL
          AND i.status = com.sosvietnam.model.payload.enums.IncidentStatus.PENDING
          AND i.routedLevel = com.sosvietnam.model.payload.enums.UnitLevel.WARD
          AND i.escalatedAt IS NULL AND i.escalateAt <= :now
        """)
    int escalate(@Param("id") String id, @Param("now") LocalDateTime now);
}
