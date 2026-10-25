package com.sosvietnam.repository;

import com.sosvietnam.model.entity.UnitDutyAssignment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UnitDutyAssignmentRepository extends JpaRepository<UnitDutyAssignment, Long> {

    Optional<UnitDutyAssignment> findFirstByAccount_IdAndEndedAtIsNull(UUID accountId);

    /** Mọi lượt trực ban đang mở (mỗi đơn vị tối đa 1) dạng [account_id, member_id], để xuất Excel. */
    @Query("SELECT d.account.id, d.member.id FROM UnitDutyAssignment d WHERE d.endedAt IS NULL")
    List<Object[]> findOpenDutyIds();

    List<UnitDutyAssignment> findByAccount_IdOrderByStartedAtDesc(UUID accountId, Pageable pageable);
}
