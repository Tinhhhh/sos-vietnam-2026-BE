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
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.SeedVersionRepository;
import com.sosvietnam.repository.StationContactRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.implement.GeoServiceImpl;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Kiểm tra GeoServiceImpl map entity -> DTO đúng và JSON không lộ geometry. */
class GeoServiceMappingTest {

    final ProvinceRepository provinceRepo = mock(ProvinceRepository.class);
    final WardRepository wardRepo = mock(WardRepository.class);
    final RescueStationRepository stationRepo = mock(RescueStationRepository.class);
    final SeedVersionRepository seedRepo = mock(SeedVersionRepository.class);
    final com.sosvietnam.util.AreaNameCache areaNames = mock(com.sosvietnam.util.AreaNameCache.class);
    final StationContactRepository contacts = mock(StationContactRepository.class);
    final com.sosvietnam.security.CurrentOfficer officer = mock(com.sosvietnam.security.CurrentOfficer.class);
    final GeoServiceImpl service = new GeoServiceImpl(provinceRepo, wardRepo, stationRepo, seedRepo, new ModelMapper(), areaNames, contacts, officer);
    final ObjectMapper json = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    final GeometryFactory gf = new GeometryFactory();

    @Test
    void stations_mapAllFields_andNoGeometryInJson() throws Exception {
        when(stationRepo.findNearestStations(anyDouble(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(List.of(
                RescueStation.builder().id(7L).name("CA Phường Ba Đình").agencyType(AgencyType.POLICE).phone("0243")
                        .address("80 Quán Thánh").province(TestGeo.province("TP. Hà Nội")).ward(TestGeo.ward("Phường Ba Đình"))
                        .latitude(21.04).longitude(105.84).location(gf.createPoint(new Coordinate(105.84, 21.04))).build()));
        when(areaNames.provinceName(TestGeo.HA_NOI.getCode())).thenReturn("Hà Nội");
        when(areaNames.wardName(TestGeo.BA_DINH.getId())).thenReturn("Phường Ba Đình");
        var list = service.findNearestStations(21, 105, AgencyType.POLICE, 1000, 5);
        String out = json.writeValueAsString(list);
        assertThat(out).contains("\"agency_type\":\"POLICE\"", "\"name\":\"CA Phường Ba Đình\"", "\"latitude\":21.04", "\"id\":7",
                        "\"province\":\"Hà Nội\"", "\"ward\":\"Phường Ba Đình\"", "\"ward_id\":102")
                .doesNotContain("location", "geometry");
    }

    @Test
    void stations_takeCommanderFromUnit_fillMissingPhoneAndEmail() {
        when(officer.orNull()).thenReturn(mock(com.sosvietnam.security.OfficerPrincipal.class));
        when(stationRepo.findNearestStations(anyDouble(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(List.of(
                RescueStation.builder().id(7L).name("CA Xã Vĩnh Lộc").agencyType(AgencyType.POLICE).officer("Trực ban CAX").build(),
                RescueStation.builder().id(8L).name("CA Phường Ba Đình").agencyType(AgencyType.POLICE).phone("0243")
                        .email("badinh@ca.vn").build(),
                RescueStation.builder().id(9L).name("Cứu hộ ABC").agencyType(AgencyType.CSGT).officer("Ông A").build()));
        when(contacts.findByStationIds(anyCollection())).thenReturn(Map.of(
                7L, new StationContactRepository.Contact("Trung tá Vũ Công Hùng", "0965080234", "congan.vinhloc.camau@sos.vn"),
                8L, new StationContactRepository.Contact("Trung tá Lê Minh An", "0911111111", "congan.badinh.hanoi@sos.vn")));
        var list = service.findNearestStations(9, 105, AgencyType.POLICE, 1000, 5);
        assertThat(list.get(0)).extracting("officer", "phone", "email")
                .containsExactly("Trung tá Vũ Công Hùng", "0965080234", "congan.vinhloc.camau@sos.vn");
        assertThat(list.get(1)).extracting("officer", "phone", "email")
                .containsExactly("Trung tá Lê Minh An", "0243", "badinh@ca.vn");
        assertThat(list.get(2).getOfficer()).isEqualTo("Ông A");
    }

    @Test
    void stations_withoutOfficer_keepOnlyStationData() {
        when(stationRepo.findNearestStations(anyDouble(), anyDouble(), any(), anyDouble(), anyInt())).thenReturn(List.of(
                RescueStation.builder().id(7L).name("CA Xã Vĩnh Lộc").agencyType(AgencyType.POLICE).build()));
        var list = service.findNearestStations(9, 105, AgencyType.POLICE, 1000, 5);
        assertThat(list.get(0)).extracting("officer", "phone", "email").containsExactly(null, null, null);
        verifyNoInteractions(contacts);
    }

    @Test
    void provincesAndWards_snakeCase() throws Exception {
        when(provinceRepo.findAllByOrderByNameAsc()).thenReturn(List.of(
                Province.builder().code("01").name("Hà Nội").nameEn("Ha Noi").centerLat(21.0).centerLng(105.8).build()));
        when(provinceRepo.existsById("01")).thenReturn(true);
        when(wardRepo.findByProvince_CodeOrderByNameAsc("01")).thenReturn(List.of(
                Ward.builder().id(1L).name("Phường Hoàn Kiếm").province(TestGeo.HA_NOI).sapNhapTu("A, B").build()));
        assertThat(json.writeValueAsString(service.getAllProvinces())).contains("\"name_en\":\"Ha Noi\"", "\"center_lat\":21.0");
        assertThat(json.writeValueAsString(service.getWardsByProvince("01"))).contains("\"province_id\":\"01\"", "\"province\":\"Hà Nội\"", "\"sap_nhap_tu\":\"A, B\"");
    }

    @Test
    void wardsOfUnknownProvince_isNotFound() {
        when(provinceRepo.existsById("99")).thenReturn(false);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.getWardsByProvince("99"))
                .isInstanceOf(com.sosvietnam.model.payload.exception.SosException.class)
                .extracting("httpStatus").isEqualTo(org.springframework.http.HttpStatus.NOT_FOUND);
    }

    @Test
    void resolve_outsideBoundaries_hasNullProvinceId() {
        when(wardRepo.findWardContainingPoint(any())).thenReturn(Optional.empty());
        when(provinceRepo.findProvinceContainingPoint(any())).thenReturn(Optional.empty());
        var r = service.resolveLocation(10, 10);
        assertThat(r.getProvinceId()).isNull();
        assertThat(r.getWard()).isEqualTo("Vùng ngoài ranh giới");
    }

    @Test
    void stationList_hasDirectoryFields_andBlankProvinceMeansNoFilter() throws Exception {
        RescueStation station = RescueStation.builder().id(9L).name("Công An TP. Cần Thơ").agencyType(AgencyType.POLICE)
                .level(StationLevel.PROVINCE).officer("Trung tá A").email("congan@cantho.gov.vn")
                .services("Tiếp nhận tin báo ANTT").facilityType("Trụ sở chính")
                .latitude(10.03).longitude(105.78).location(gf.createPoint(new Coordinate(105.78, 10.03))).build();
        when(stationRepo.findAll(ArgumentMatchers.<Specification<RescueStation>>any(), any(Pageable.class)))
                .thenAnswer(i -> new PageImpl<>(List.of(station), i.getArgument(1), 21));

        ListResponse page = service.getStations(2, 10, "c", AgencyType.POLICE, null, "name", "DESC");
        assertThat(page.getPageNo()).isEqualTo(2);
        assertThat(page.getTotalElements()).isEqualTo(21);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.isLast()).isTrue();

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(stationRepo).findAll(ArgumentMatchers.<Specification<RescueStation>>any(), pageable.capture());
        assertThat(pageable.getValue().getSort().getOrderFor("name").getDirection()).isEqualTo(Sort.Direction.DESC);

        String out = json.writeValueAsString(page);
        assertThat(out).contains("\"content\":[", "\"level\":\"PROVINCE\"", "\"officer\":\"Trung tá A\"",
                        "\"email\":\"congan@cantho.gov.vn\"", "\"services\":\"Tiếp nhận tin báo ANTT\"",
                        "\"facility_type\":\"Trụ sở chính\"", "\"total_pages\":3")
                .doesNotContain("location", "code");
    }

    @Test
    void stationList_rejectsBadPagingAndSort() {
        assertThatThrownBy(() -> service.getStations(-1, 10, null, null, null, "id", "asc")).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getStations(0, 0, null, null, null, "id", "asc")).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getStations(0, 1001, null, null, null, "id", "asc")).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getStations(0, 10, null, null, null, "location", "asc")).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getStations(0, 10, null, null, null, "id", "up")).isInstanceOf(SosException.class);
        verifyNoInteractions(stationRepo);
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
