package com.sosvietnam.security;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.enums.UnitRole;

import java.security.Principal;
import java.util.UUID;

/**
 * Một cán bộ đang trực (unit_members) cùng phạm vi đơn vị của tài khoản (lực lượng, cấp, địa bàn).
 * memberId = người (giữ ca, điều động, ký...); unitAccountId = tài khoản đơn vị (cổng vào).
 * Dùng làm Principal của request REST (JwtAuthenticationFilter) và của phiên STOMP: getName() = member id,
 * nên nhiều cán bộ cùng tài khoản đơn vị vẫn là các người dùng STOMP riêng (/user/queue/... gửi đúng người).
 * IncidentAccess so địa bàn bằng mã tỉnh / id xã; provinceName / wardName chỉ để hiển thị.
 */
public record OfficerPrincipal(UUID memberId, UUID unitAccountId, String email, String fullName, RoleName role,
                               UnitRole unitRole, AgencyType agency, UnitLevel level,
                               String provinceId, String provinceName, Long wardId, String wardName)
        implements Principal {

    public static OfficerPrincipal of(UnitMember member) {
        Account account = member.getAccount();
        RoleName role = account.getRole() == null ? null
                : RoleName.fromRole(account.getRole().getRoleName()).orElse(null);
        return new OfficerPrincipal(member.getId(), account.getId(), account.getEmail(), member.displayName(), role,
                member.getUnitRole(), account.getAgencyType(), account.getUnitLevel(),
                account.provinceId(), account.provinceName(), account.wardId(), account.wardName());
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    public boolean isStaff() {
        return role != null && role.isStaff();
    }

    public boolean isCommander() {
        return unitRole == UnitRole.COMMANDER;
    }

    /** Cùng một người (so member id). */
    public boolean is(UUID memberId) {
        return memberId != null && memberId.equals(this.memberId);
    }

    @Override
    public String getName() {
        return memberId.toString();
    }
}
