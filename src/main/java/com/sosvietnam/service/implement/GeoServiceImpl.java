package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.model.payload.response.ProvinceResponse;
import com.sosvietnam.model.payload.response.StationResponse;
import com.sosvietnam.model.payload.response.WardResponse;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Tra cứu địa giới bằng PostGIS:
 * - resolveLocation: điểm (lat, lng) nằm trong xã/phường nào, nếu không có thì tỉnh nào.
 * - findNearestStations: trạm gần nhất trong bán kính, lọc theo lực lượng.
 * Entity có cột geometry nên luôn map sang DTO trước khi trả ra ngoài.
 */
@Service
@RequiredArgsConstructor
public class GeoServiceImpl implements GeoService {

    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final RescueStationRepository rescueStationRepository;
    private final ModelMapper modelMapper;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    public List<ProvinceResponse> getAllProvinces() {
        return provinceRepository.findAllByOrderByNameAsc().stream()
                .map(province -> modelMapper.map(province, ProvinceResponse.class))
                .toList();
    }

    @Override
    public List<WardResponse> getWardsByProvince(String provinceCode) {
        return wardRepository.findByProvinceCode(provinceCode).stream()
                .map(ward -> modelMapper.map(ward, WardResponse.class))
                .toList();
    }

    @Override
    public LocationResponse resolveLocation(double lat, double lng) {
        // JTS Coordinate nhận (x = lng, y = lat)
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);

        LocationResponse result = LocationResponse.builder()
                .latitude(lat)
                .longitude(lng)
                .build();

        // 1. Ưu tiên tìm xã/phường chứa điểm
        Optional<Ward> wardOpt = wardRepository.findWardContainingPoint(point);
        if (wardOpt.isPresent()) {
            Ward ward = wardOpt.get();
            result.setWard(ward.getName());
            result.setProvince(ward.getProvinceName());
            result.setProvinceCode(ward.getProvinceCode());
            result.setSapNhapTu(ward.getSapNhapTu());
            return result;
        }

        // 2. Không có xã/phường thì tìm tỉnh chứa điểm
        Optional<Province> provOpt = provinceRepository.findProvinceContainingPoint(point);
        if (provOpt.isPresent()) {
            Province prov = provOpt.get();
            result.setProvince(prov.getName());
            result.setProvinceCode(prov.getCode());
            result.setWard("Chưa xác định xã/phường");
            return result;
        }

        // 3. Ngoài ranh giới dữ liệu: province_code = null để FE biết là chưa xác định
        result.setProvince("Việt Nam");
        result.setWard("Vùng ngoài ranh giới");
        return result;
    }

    @Override
    public List<StationResponse> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit) {
        String agencyStr = agencyType != null ? agencyType.name() : null;
        return rescueStationRepository.findNearestStations(lat, lng, agencyStr, radiusMeters, limit).stream()
                .map(station -> modelMapper.map(station, StationResponse.class))
                .toList();
    }
}
