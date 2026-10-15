package com.sosvietnam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.SeedVersion;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.SeedVersionRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.implement.GeoServiceImpl;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.modelmapper.ModelMapper;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Kiểm tra GeoServiceImpl map entity -> DTO đúng và JSON không lộ geometry. */
class GeoServiceMappingTest {

    final ProvinceRepository provinceRepo = mock(ProvinceRepository.class);
    final WardRepository wardRepo = mock(WardRepository.class);
    final RescueStationRepository stationRepo = mock(RescueStationRepository.class);
    final SeedVersionRepository seedRepo = mock(SeedVersionRepository.class);
    final GeoServiceImpl service = new GeoServiceImpl(provinceRepo, wardRepo, stationRepo, seedRepo, new ModelMapper());
    final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final GeometryFactory gf = new GeometryFactory();

    @Test
    void stations_mapAllFields_andNoGeometryInJson() throws Exception {
        when(stationRepo.findNearestStations(anyDouble(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(List.of(
                RescueStation.builder().id(7L).name("CA Phường Ba Đình").agencyType(AgencyType.POLICE).phone("0243")
                        .address("80 Quán Thánh").province("TP. Hà Nội").ward("Phường Ba Đình")
                        .latitude(21.04).longitude(105.84).location(gf.createPoint(new Coordinate(105.84, 21.04))).build()));
        var list = service.findNearestStations(21, 105, AgencyType.POLICE, 1000, 5);
        String out = json.writeValueAsString(list);
        assertThat(out).contains("\"agency_type\":\"POLICE\"", "\"name\":\"CA Phường Ba Đình\"", "\"latitude\":21.04", "\"id\":7")
                .doesNotContain("location", "geometry");
    }

    @Test
    void provincesAndWards_snakeCase() throws Exception {
        when(provinceRepo.findAllByOrderByNameAsc()).thenReturn(List.of(
                Province.builder().code("01").name("Hà Nội").nameEn("Ha Noi").centerLat(21.0).centerLng(105.8).build()));
        when(wardRepo.findByProvinceCode("01")).thenReturn(List.of(
                Ward.builder().id(1L).name("Phường Hoàn Kiếm").provinceCode("01").provinceName("Hà Nội").sapNhapTu("A, B").build()));
        assertThat(json.writeValueAsString(service.getAllProvinces())).contains("\"name_en\":\"Ha Noi\"", "\"center_lat\":21.0");
        assertThat(json.writeValueAsString(service.getWardsByProvince("01"))).contains("\"province_code\":\"01\"", "\"sap_nhap_tu\":\"A, B\"");
    }

    @Test
    void resolve_outsideBoundaries_hasNullProvinceCode() {
        when(wardRepo.findWardContainingPoint(any())).thenReturn(Optional.empty());
        when(provinceRepo.findProvinceContainingPoint(any())).thenReturn(Optional.empty());
        var r = service.resolveLocation(10, 10);
        assertThat(r.getProvinceCode()).isNull();
        assertThat(r.getWard()).isEqualTo("Vùng ngoài ranh giới");
    }

    @Test
    void stationList_hasDirectoryFields_andBlankProvinceMeansNoFilter() throws Exception {
        when(stationRepo.findStations(isNull(), eq(AgencyType.CSGT), isNull())).thenReturn(List.of(
                RescueStation.builder().id(9L).name("Phòng CSGT Công An TP. Cần Thơ").agencyType(AgencyType.CSGT)
                        .level(StationLevel.PROVINCE).officer("Trung tá A").email("csgt@cantho.gov.vn")
                        .services("Xử lý vi phạm giao thông").facilityType("Trụ sở chính")
                        .latitude(10.03).longitude(105.78).location(gf.createPoint(new Coordinate(105.78, 10.03))).build()));
        String out = json.writeValueAsString(service.getStations("  ", AgencyType.CSGT, null));
        assertThat(out).contains("\"level\":\"PROVINCE\"", "\"officer\":\"Trung tá A\"", "\"email\":\"csgt@cantho.gov.vn\"",
                        "\"services\":\"Xử lý vi phạm giao thông\"", "\"facility_type\":\"Trụ sở chính\"")
                .doesNotContain("location", "code");
    }

    @Test
    void dataStatus_listsBothDatasets_evenBeforeFirstSync() throws Exception {
        Instant applied = Instant.parse("2026-10-15T01:00:00Z");
        when(seedRepo.findById(SeedVersion.RESCUE_STATIONS)).thenReturn(Optional.of(SeedVersion.builder()
                .name(SeedVersion.RESCUE_STATIONS).checksum("x").version("2026.10.15").appliedAt(applied).checkedAt(applied).build()));
        when(seedRepo.findById(SeedVersion.BOUNDARIES)).thenReturn(Optional.empty());
        when(wardRepo.count()).thenReturn(3321L);
        when(stationRepo.count()).thenReturn(3717L);

        var status = service.getDataStatus();
        assertThat(status).extracting("dataset").containsExactly("boundaries", "rescue_stations");
        assertThat(status.get(0).getVersion()).isNull();
        assertThat(status.get(0).getTotal()).isEqualTo(3321);
        assertThat(json.writeValueAsString(status.get(1)))
                .contains("\"version\":\"2026.10.15\"", "\"updated_at\":\"2026-10-15T01:00:00Z\"", "\"total\":3717");
    }
}
