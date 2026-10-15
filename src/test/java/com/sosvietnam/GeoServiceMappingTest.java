package com.sosvietnam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.implement.GeoServiceImpl;
import org.junit.jupiter.api.Test;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.modelmapper.ModelMapper;

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
    final GeoServiceImpl service = new GeoServiceImpl(provinceRepo, wardRepo, stationRepo, new ModelMapper());
    final ObjectMapper json = new ObjectMapper();
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
}
