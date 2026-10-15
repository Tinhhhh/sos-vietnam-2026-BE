package com.sosvietnam.config;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cách DataSeeder đọc "agency"/"level" trong data/rescue_stations.json. */
class StationSeedMappingTest {

    @Test
    void csgt_isNoLongerFoldedIntoTrafficRescue() {
        assertThat(DataSeeder.toAgencyType("csgt", StationLevel.PROVINCE, "Phòng CSGT Công An Sơn La"))
                .isEqualTo(AgencyType.CSGT);
        assertThat(DataSeeder.toAgencyType("csgt", StationLevel.WARD, "Đội CSGT số 1"))
                .isEqualTo(AgencyType.CSGT);
        assertThat(DataSeeder.toAgencyType("traffic-rescue", StationLevel.ENTERPRISE, "Cứu Hộ Giao Thông 116 Hà Nội"))
                .isEqualTo(AgencyType.TRAFFIC_RESCUE);
    }

    @Test
    void rescueCompaniesTaggedCsgt_becomeTrafficRescue_butCsgtUnitsStayCsgt() {
        assertThat(DataSeeder.toAgencyType("csgt", StationLevel.ENTERPRISE, "Cứu Hộ Giao Thông Đà Nẵng"))
                .isEqualTo(AgencyType.TRAFFIC_RESCUE);
        assertThat(DataSeeder.toAgencyType("csgt", StationLevel.ENTERPRISE, "Phòng CSGT Công An TP. Đà Nẵng"))
                .isEqualTo(AgencyType.CSGT);
    }

    @Test
    void otherAgencies_andUnknownFallBackToPolice() {
        assertThat(DataSeeder.toAgencyType("fire", StationLevel.WARD, "Đội PCCC")).isEqualTo(AgencyType.FIRE);
        assertThat(DataSeeder.toAgencyType("hospital", StationLevel.PROVINCE, "BV")).isEqualTo(AgencyType.HOSPITAL);
        assertThat(DataSeeder.toAgencyType("police", StationLevel.WARD, "CA")).isEqualTo(AgencyType.POLICE);
        assertThat(DataSeeder.toAgencyType("xyz", null, "?")).isEqualTo(AgencyType.POLICE);
    }

    @Test
    void level_parsesFileValues() {
        assertThat(DataSeeder.toStationLevel("enterprise")).isEqualTo(StationLevel.ENTERPRISE);
        assertThat(DataSeeder.toStationLevel("ward")).isEqualTo(StationLevel.WARD);
        assertThat(DataSeeder.toStationLevel("district")).isNull();
        assertThat(DataSeeder.toStationLevel("")).isNull();
    }
}
