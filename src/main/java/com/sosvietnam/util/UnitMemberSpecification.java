package com.sosvietnam.util;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import jakarta.persistence.criteria.Join;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collection;
import java.util.UUID;

/** Bộ lọc cho GET /api/v1/officers/status (cán bộ của đơn vị); tham số null = không lọc. */
@UtilityClass
public class UnitMemberSpecification {

    /** Cán bộ đang hoạt động của tài khoản OFFICER đang hoạt động (không lấy cán bộ ẩn của admin). */
    public Specification<UnitMember> activeOfficers() {
        return (root, query, cb) -> {
            Join<UnitMember, Account> account = account(root);
            return cb.and(cb.isTrue(root.get("active")), cb.isTrue(account.get("isActive")),
                    cb.equal(account.get("role").get("roleName"), RoleName.OFFICER.getRole()));
        };
    }

    public Specification<UnitMember> hasAgency(AgencyType agency) {
        return (root, query, cb) -> agency == null ? null : cb.equal(account(root).get("agencyType"), agency);
    }

    public Specification<UnitMember> hasLevel(UnitLevel level) {
        return (root, query, cb) -> level == null ? null : cb.equal(account(root).get("unitLevel"), level);
    }

    /** Tỉnh của đơn vị (provinces.code). */
    public Specification<UnitMember> inProvince(String provinceId) {
        return (root, query, cb) -> provinceId == null ? null
                : cb.equal(account(root).get("province").get("code"), provinceId);
    }

    /** Xã của đơn vị (đơn vị cấp tỉnh: xã đặt trụ sở). */
    public Specification<UnitMember> inWard(Long wardId) {
        return (root, query, cb) -> wardId == null ? null : cb.equal(account(root).get("ward").get("id"), wardId);
    }

    /** Chỉ những cán bộ trong danh sách (đang trực tuyến); danh sách rỗng = không ai. */
    public Specification<UnitMember> idIn(Collection<UUID> ids) {
        return (root, query, cb) -> ids == null ? null : ids.isEmpty() ? cb.disjunction() : root.get("id").in(ids);
    }

    /** Join tới tài khoản đơn vị, dùng chung cho mọi điều kiện (không join lặp mỗi điều kiện một lần). */
    @SuppressWarnings("unchecked")
    private Join<UnitMember, Account> account(jakarta.persistence.criteria.Root<UnitMember> root) {
        return (Join<UnitMember, Account>) root.getJoins().stream()
                .filter(j -> j.getAttribute().getName().equals("account"))
                .findFirst()
                .orElseGet(() -> root.join("account"));
    }
}
