package com.sosvietnam.config;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cách DataSeeder đọc "agency"/"level" trong data/rescue_stations.json. */
class StationSeedMappingTest {

    @Test
    void csgtAndTrafficRescue_areStoredAsPolice() {
        assertThat(DataSeeder.toAgencyType("csgt")).isEqualTo(AgencyType.POLICE);
        assertThat(DataSeeder.toAgencyType("traffic-rescue")).isEqualTo(AgencyType.POLICE);
        assertThat(DataSeeder.toAgencyType("police")).isEqualTo(AgencyType.POLICE);
        assertThat(DataSeeder.toAgencyType("xyz")).isEqualTo(AgencyType.POLICE);
    }

    @Test
    void fireHospitalAndMobilePolice_keepTheirAgency() {
        assertThat(DataSeeder.toAgencyType("fire")).isEqualTo(AgencyType.FIRE);
        assertThat(DataSeeder.toAgencyType("hospital")).isEqualTo(AgencyType.HOSPITAL);
        assertThat(DataSeeder.toAgencyType("mobile-police")).isEqualTo(AgencyType.MOBILE_POLICE);
    }

    @Test
    void level_parsesFileValues_unknownIsNull() {
        assertThat(DataSeeder.toStationLevel("ward")).isEqualTo(StationLevel.WARD);
        assertThat(DataSeeder.toStationLevel("province")).isEqualTo(StationLevel.PROVINCE);
        assertThat(DataSeeder.toStationLevel("national")).isEqualTo(StationLevel.NATIONAL);
        assertThat(DataSeeder.toStationLevel("enterprise")).isEqualTo(StationLevel.ENTERPRISE);
        assertThat(DataSeeder.toStationLevel("district")).isNull();
        assertThat(DataSeeder.toStationLevel("")).isNull();
    }
}
