package com.sosvietnam.repository;

import com.sosvietnam.model.entity.UnitMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface UnitMemberRepository extends JpaRepository<UnitMember, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<UnitMember> {

    List<UnitMember> findByAccount_IdAndActiveTrueOrderByFullNameAsc(UUID accountId);

    List<UnitMember> findByAccount_IdInAndActiveTrue(Collection<UUID> accountIds);

    long countByAccount_IdAndActiveTrue(UUID accountId);

    boolean existsByAccount_IdAndBadgeNumberAndActiveTrueAndIdNot(UUID accountId, String badgeNumber, UUID id);

    /** Cán bộ đang hoạt động của tài khoản đơn vị đang hoạt động (đơn vị đúng lực lượng, cấp). */
    @Query("""
        SELECT m FROM UnitMember m JOIN FETCH m.account a
        WHERE m.active = true AND a.isActive = true AND a.agencyType = :agency
          AND a.unitLevel = :level
        """)
    List<UnitMember> findActiveOfficers(@Param("agency") com.sosvietnam.model.payload.enums.AgencyType agency,
                                       @Param("level") com.sosvietnam.model.payload.enums.UnitLevel level);

    /** Cán bộ đang hoạt động của các tài khoản OFFICER để xuất Excel, cùng bộ lọc với AccountRepository.findForExport. */
    @Query("""
        SELECT m FROM UnitMember m JOIN FETCH m.account a
        WHERE m.active = true AND a.role.roleName = 'OFFICER'
          AND (:provinceId IS NULL OR a.province.code = :provinceId)
          AND (:agency IS NULL OR a.agencyType = :agency)
          AND (:level IS NULL OR a.unitLevel = :level)
        """)
    List<UnitMember> findForExport(@Param("provinceId") String provinceId,
                                   @Param("agency") com.sosvietnam.model.payload.enums.AgencyType agency,
                                   @Param("level") com.sosvietnam.model.payload.enums.UnitLevel level);
}
