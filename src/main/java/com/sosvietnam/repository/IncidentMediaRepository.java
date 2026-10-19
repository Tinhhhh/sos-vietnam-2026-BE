package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface IncidentMediaRepository extends JpaRepository<IncidentMedia, Long> {

    List<IncidentMedia> findByIncident_IdOrderByCreatedAtAsc(String incidentId);

    Optional<IncidentMedia> findByIncident_IdAndFileName(String incidentId, String fileName);

    long countByIncident_Id(String incidentId);

    /** Số file theo loại của nhiều ca một lúc (hàng đợi): [incidentId, kind, count]. */
    @Query("SELECT m.incident.id, m.kind, count(m) FROM IncidentMedia m WHERE m.incident.id IN :ids "
            + "GROUP BY m.incident.id, m.kind")
    List<Object[]> countByIncidentIdsAndKind(@Param("ids") Collection<String> ids);
}
