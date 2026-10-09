package com.sosvietnam.repository;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface IncidentRepository extends JpaRepository<Incident, String> {

    List<Incident> findByStatus(IncidentStatus status);

    List<Incident> findTop50ByOrderByCreatedAtDesc();

    List<Incident> findByProvinceOrderByCreatedAtDesc(String province);
}
