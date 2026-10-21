package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentRoute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IncidentRouteRepository extends JpaRepository<IncidentRoute, Long> {

    /** Tuyến đang dùng của một lượt điều động (chưa bị thay). */
    Optional<IncidentRoute> findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(Long dispatchId);
}
