package com.sosvietnam.config;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Cách DataSeeder đọc "agency"/"level" trong data/rescue_stations.json. */
class StationSeedMappingTest {

    @Test
    void csgtAndTrafficRescue_areStoredAsCsgt() {
        assertThat(DataSeeder.toAgencyType("csgt")).isEqualTo(AgencyType.CSGT);
        assertThat(DataSeeder.toAgencyType("traffic-rescue")).isEqualTo(AgencyType.CSGT);
        assertThat(DataSeeder.toAgencyType("police")).isEqualTo(AgencyType.POLICE);
        assertThat(DataSeeder.toAgencyType("xyz")).isEqualTo(AgencyType.POLICE);
    }

    @Test
    void fireAndHospital_keepTheirAgency() {
        assertThat(DataSeeder.toAgencyType("fire")).isEqualTo(AgencyType.FIRE);
        assertThat(DataSeeder.toAgencyType("hospital")).isEqualTo(AgencyType.HOSPITAL);
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
