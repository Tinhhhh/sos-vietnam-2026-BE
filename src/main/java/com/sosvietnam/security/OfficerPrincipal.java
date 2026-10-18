package com.sosvietnam.security;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;

import java.security.Principal;
import java.util.UUID;

/**
 * Phạm vi của một cán bộ (lực lượng, cấp, địa bàn), chụp lại từ tài khoản.
 * Dùng làm Principal của phiên STOMP (getName() = email, Spring gửi /user/queue/... theo email)
 * và để kiểm tra quyền xem / nhận ca trong IncidentAccess (so địa bàn bằng mã tỉnh / id xã).
 * provinceName / wardName chỉ để hiển thị (tên đơn vị).
 */
public record OfficerPrincipal(UUID accountId, String email, String fullName, RoleName role,
                               AgencyType agency, UnitLevel level,
                               String provinceId, String provinceName, Long wardId, String wardName)
        implements Principal {

    public static OfficerPrincipal of(Account account) {
        RoleName role = account.getRole() == null ? null
                : RoleName.fromRole(account.getRole().getRoleName()).orElse(null);
        return new OfficerPrincipal(account.getId(), account.getEmail(), account.fullName(), role,
                account.getAgencyType(), account.getUnitLevel(),
                account.provinceId(), account.provinceName(), account.wardId(), account.wardName());
    }

    public boolean isAdmin() {
        return role == RoleName.ADMIN;
    }

    public boolean isStaff() {
        return role != null && role.isStaff();
    }

    @Override
    public String getName() {
        return email;
    }
}
