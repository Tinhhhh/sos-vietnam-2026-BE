package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentDispatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IncidentDispatchRepository extends JpaRepository<IncidentDispatch, IncidentDispatch.Key> {

    List<IncidentDispatch> findByIncident_IdOrderByDispatchedAtAsc(String incidentId);

    long countByIncident_Id(String incidentId);

    boolean existsByIncident_IdAndStation_Id(String incidentId, Long stationId);
}
