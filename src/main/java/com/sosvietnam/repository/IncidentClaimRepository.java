package com.sosvietnam.repository;

import com.sosvietnam.model.entity.IncidentClaim;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface IncidentClaimRepository extends JpaRepository<IncidentClaim, Long> {

    /** Lượt giữ ca đang mở của một trực ban trên một ca. */
    Optional<IncidentClaim> findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(String incidentId, UUID memberId);

    /** Lượt nhận ca gần nhất (cán bộ xử lý cuối cùng, cho lịch sử). */
    Optional<IncidentClaim> findFirstByIncident_IdOrderByClaimedAtDesc(String incidentId);

    /** Toàn bộ lượt giữ ca, cũ trước (hộp chi tiết ca). */
    java.util.List<IncidentClaim> findByIncident_IdOrderByClaimedAtAsc(String incidentId);
}
