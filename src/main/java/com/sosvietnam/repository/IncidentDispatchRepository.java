package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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

    /** Lượt chủ trì đang hoạt động của ca (không tính lượt đã bị từ chối). */
    Optional<IncidentDispatch> findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(
            String incidentId, DispatchRole role, DispatchStatus status);
}
