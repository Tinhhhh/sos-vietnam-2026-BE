package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentClaimRepository extends JpaRepository<IncidentClaim, Long> {

    /** Lượt giữ ca đang mở của một trực ban trên một ca. */
    Optional<IncidentClaim> findFirstByIncident_IdAndAccount_IdAndReleasedAtIsNull(String incidentId, UUID accountId);
}
