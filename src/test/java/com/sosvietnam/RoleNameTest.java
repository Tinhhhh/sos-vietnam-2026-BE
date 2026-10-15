package com.sosvietnam;

import com.sosvietnam.model.payload.enums.RoleName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RoleNameTest {

    @Test
    void fromRole_matchesDbValue_caseInsensitive() {
        assertThat(RoleName.fromRole("OFFICER")).contains(RoleName.OFFICER);
        assertThat(RoleName.fromRole("admin")).contains(RoleName.ADMIN);
        assertThat(RoleName.fromRole("ROLE_ADMIN")).isEmpty();
        assertThat(RoleName.fromRole(null)).isEmpty();
        assertThat(RoleName.isExistRole("DISPATCHER")).isFalse();
    }

    @Test
    void onlyAdminAndOfficerAreStaff() {
        assertThat(RoleName.ADMIN.isStaff()).isTrue();
        assertThat(RoleName.OFFICER.isStaff()).isTrue();
        assertThat(RoleName.CITIZEN.isStaff()).isFalse();
    }
}
