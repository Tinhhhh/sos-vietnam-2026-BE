package com.sosvietnam.repository;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {
    Optional<Account> findByEmail(String email);
    Boolean existsByEmail(String email);

    /** Trực ban còn hoạt động của một lực lượng ở một cấp (lọc địa bàn ở Java vì tên tỉnh/xã cần chuẩn hoá). */
    @Query("SELECT a FROM Account a WHERE a.agencyType = :agency AND a.unitLevel = :level AND a.isActive = true")
    List<Account> findActiveOfficers(@Param("agency") AgencyType agency, @Param("level") UnitLevel level);

    /** Mọi tài khoản đang hoạt động của một vai trò (ADMIN xem trạng thái cán bộ toàn quốc). */
    List<Account> findByRole_RoleNameAndIsActiveTrue(String roleName);

    /** Xã có ít nhất 1 cán bộ của lực lượng này ở cấp xã đang hoạt động (để phân luồng ca vào xã). */
    boolean existsByAgencyTypeAndUnitLevelAndWard_IdAndIsActiveTrue(AgencyType agency, UnitLevel level, Long wardId);

    /** Cán bộ cấp xã đang hoạt động của một lực lượng ở một xã (đối chiếu ai đang trực tuyến lúc phân luồng). */
    List<Account> findByAgencyTypeAndUnitLevelAndWard_IdAndIsActiveTrue(AgencyType agency, UnitLevel level, Long wardId);
}
