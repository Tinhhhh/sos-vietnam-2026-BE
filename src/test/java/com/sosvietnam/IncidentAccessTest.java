package com.sosvietnam;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.util.IncidentAccess;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Luật ai thấy / ai được nhận ca (module 5). */
class IncidentAccessTest {

    static OfficerPrincipal officer(AgencyType agency, UnitLevel level, String province, String ward) {
        return TestGeo.officer(UUID.randomUUID(), "x@sos.vn", "X", RoleName.OFFICER, agency, level, province, ward);
    }

    static final OfficerPrincipal ADMIN = TestGeo.officer(UUID.randomUUID(), "admin@sos.vn", "Admin",
            RoleName.ADMIN, null, UnitLevel.NATIONAL, "TP. Hà Nội", null);
    static final OfficerPrincipal CA_TAN_AN = officer(AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");
    static final OfficerPrincipal CA_CAN_THO = officer(AgencyType.POLICE, UnitLevel.PROVINCE, "Cần Thơ", null);
    static final OfficerPrincipal PCCC_CAN_THO = officer(AgencyType.FIRE, UnitLevel.PROVINCE, "Cần Thơ", null);
    static final OfficerPrincipal PCCC_DOI_TAN_AN = officer(AgencyType.FIRE, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");

    static Incident wardPolice() {
        return Incident.builder().id("SOS-1").routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).escalateAt(LocalDateTime.now().plusMinutes(15)).build();
    }

    @Test
    void wardPoliceIncident_wardHandles_provinceOnlyWatches() {
        Incident inc = wardPolice();
        assertThat(IncidentAccess.canHandle(CA_TAN_AN, inc)).isTrue();
        assertThat(IncidentAccess.canView(CA_CAN_THO, inc)).isTrue();
        assertThat(IncidentAccess.canHandle(CA_CAN_THO, inc)).isFalse();
        assertThat(IncidentAccess.canView(officer(AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Cái Khế"), inc)).isFalse();
        assertThat(IncidentAccess.canView(officer(AgencyType.POLICE, UnitLevel.PROVINCE, "Hà Nội", null), inc)).isFalse();
    }

    @Test
    void escalatedIncident_provinceHandles_wardStillWatches() {
        Incident inc = wardPolice();
        inc.setRoutedLevel(UnitLevel.PROVINCE);
        inc.setEscalatedAt(LocalDateTime.now());
        assertThat(IncidentAccess.canView(CA_TAN_AN, inc)).isTrue();
        assertThat(IncidentAccess.canHandle(CA_TAN_AN, inc)).isFalse();
        assertThat(IncidentAccess.canHandle(CA_CAN_THO, inc)).isTrue();
    }

    @Test
    void routedToProvinceAtCreation_noWardOfficerOnline_wardStillWatches() {
        Incident inc = wardPolice();
        inc.setRoutedLevel(UnitLevel.PROVINCE);
        inc.setEscalateAt(null);
        assertThat(IncidentAccess.canView(CA_TAN_AN, inc)).isTrue();
        assertThat(IncidentAccess.canHandle(CA_TAN_AN, inc)).isFalse();
        assertThat(IncidentAccess.canHandle(CA_CAN_THO, inc)).isTrue();
        assertThat(IncidentAccess.canView(officer(AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Cái Khế"), inc)).isFalse();
    }

    @Test
    void otherAgenciesNeverSeeEachOther_andFireTeamsDoNotStandDuty() {
        Incident police = wardPolice();
        assertThat(IncidentAccess.canView(PCCC_CAN_THO, police)).isFalse();

        Incident fire = Incident.builder().id("SOS-2").routedAgency(AgencyType.FIRE).routedLevel(UnitLevel.PROVINCE)
                .province(TestGeo.province("TP. Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).build();
        assertThat(IncidentAccess.canHandle(PCCC_CAN_THO, fire)).isTrue();
        // Đội PCCC không trực (PCCC nhận ca từ cấp phòng) nên đội không thấy
        assertThat(IncidentAccess.canView(PCCC_DOI_TAN_AN, fire)).isFalse();
        assertThat(IncidentAccess.canView(CA_TAN_AN, fire)).isFalse();
    }

    @Test
    void admin_seesAll_handlesOnlyNationalLevel() {
        assertThat(IncidentAccess.canView(ADMIN, wardPolice())).isTrue();
        assertThat(IncidentAccess.canHandle(ADMIN, wardPolice())).isFalse();
        Incident unknownArea = Incident.builder().id("SOS-3").routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.NATIONAL).build();
        assertThat(IncidentAccess.canHandle(ADMIN, unknownArea)).isTrue();
        assertThat(IncidentAccess.canView(CA_CAN_THO, unknownArea)).isFalse();
    }

    @Test
    void areaNames_ignoreAccentsCaseAndCityPrefix() {
        // Luật quyền xem so khoá; AreaNames chỉ dùng cho tên gõ từ ngoài (bộ lọc admin, file seed)
        assertThat(com.sosvietnam.util.AreaNames.provinceKey("TP. Cần Thơ")).isEqualTo("can tho");
        assertThat(com.sosvietnam.util.AreaNames.provinceKey("Thành phố Hồ Chí Minh"))
                .isEqualTo(com.sosvietnam.util.AreaNames.provinceKey("TP. Hồ Chí Minh"));
        assertThat(com.sosvietnam.util.AreaNames.key("Phường  Tân An")).isEqualTo("phuong tan an");
        assertThat(com.sosvietnam.util.AreaNames.key(null)).isNull();
    }

    @Test
    void unknownArea_neverMatches() {
        Incident noWard = wardPolice();
        noWard.setWard(null);
        assertThat(IncidentAccess.canView(officer(AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", null), noWard)).isFalse();
    }

    @Test
    void sameUnit_forHandover() {
        assertThat(IncidentAccess.sameUnit(CA_TAN_AN, officer(AgencyType.POLICE, UnitLevel.WARD, "TP. Cần Thơ", "Phường Tân An"))).isTrue();
        assertThat(IncidentAccess.sameUnit(CA_TAN_AN, CA_CAN_THO)).isFalse();
        assertThat(IncidentAccess.sameUnit(CA_CAN_THO, PCCC_CAN_THO)).isFalse();
    }
}
