package com.sosvietnam.security;

import com.sosvietnam.model.payload.exception.SosException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Cán bộ của request hiện tại (JwtAuthenticationFilter gắn OfficerPrincipal làm principal). */
@Component
public class CurrentOfficer {

    /** Cán bộ (OFFICER / ADMIN) đang đăng nhập; chưa đăng nhập 401, không phải cán bộ 403. */
    public OfficerPrincipal require() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        if (!(authentication.getPrincipal() instanceof OfficerPrincipal officer) || !officer.isStaff()) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ mới thực hiện được thao tác này");
        }
        return officer;
    }

    /** Cán bộ đang đăng nhập, hoặc null (vd người dân gọi API công khai). */
    public OfficerPrincipal orNull() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof OfficerPrincipal officer
                && officer.isStaff() ? officer : null;
    }
}
