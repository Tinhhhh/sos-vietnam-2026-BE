package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.SeedVersion;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.DataStatusResponse;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.model.payload.response.ProvinceResponse;
import com.sosvietnam.model.payload.response.StationResponse;
import com.sosvietnam.model.payload.response.WardResponse;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.SeedVersionRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.util.AppConstants;
import com.sosvietnam.util.RescueStationSpecification;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
    private final SeedVersionRepository seedVersionRepository;
    private final ModelMapper modelMapper;
    // Field của RescueStation được phép sort (tên field Java, không phải tên cột)
    private static final List<String> STATION_SORT_FIELDS = List.of("id", "name", "province", "ward", "agencyType", "level");

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
            result.setWard(UNKNOWN_WARD);
            return result;
        }

        // 3. Ngoài ranh giới dữ liệu: province_code = null để FE biết là chưa xác định
        result.setProvince("Việt Nam");
        result.setWard(OUTSIDE_BOUNDARIES);
        return result;
    }

    @Override
    public List<StationResponse> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit) {
        String agencyStr = agencyType != null ? agencyType.name() : null;
        return rescueStationRepository.findNearestStations(lat, lng, agencyStr, radiusMeters, limit).stream()
                .map(station -> modelMapper.map(station, StationResponse.class))
                .toList();
    }

    @Override
    public ListResponse getStations(int pageNo, int pageSize, String province, AgencyType agencyType, StationLevel level,
                                    String sortBy, String sortDir) {
        if (pageNo < 0 || pageSize < 1 || pageSize > AppConstants.MAX_PAGE_SIZE) {
            throw new SosException("pageNo phải >= 0, pageSize từ 1 đến " + AppConstants.MAX_PAGE_SIZE);
        }
        if (!STATION_SORT_FIELDS.contains(sortBy)) {
            throw new SosException("sortBy chỉ nhận: " + String.join(", ", STATION_SORT_FIELDS));
        }
        if (!"asc".equalsIgnoreCase(sortDir) && !"desc".equalsIgnoreCase(sortDir)) {
            throw new SosException("sortDir chỉ nhận asc hoặc desc");
        }
        Sort sort = Sort.by(sortDir.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC, sortBy);
        Pageable pageable = PageRequest.of(pageNo, pageSize, sort);

        Specification<RescueStation> spec = Specification.where(RescueStationSpecification.provinceContains(province))
                .and(RescueStationSpecification.hasAgencyType(agencyType))
                .and(RescueStationSpecification.hasLevel(level));
        Page<RescueStation> stations = rescueStationRepository.findAll(spec, pageable);

        ListResponse listResponse = new ListResponse();
        listResponse.setContent(stations.getContent().stream()
                .map(station -> modelMapper.map(station, StationResponse.class))
                .toList());
        listResponse.setPageNo(pageNo);
        listResponse.setPageSize(pageSize);
        listResponse.setTotalElements(stations.getTotalElements());
        listResponse.setTotalPages(stations.getTotalPages());
        listResponse.setLast(stations.isLast());
        return listResponse;
    }

    @Override
    public List<DataStatusResponse> getDataStatus() {
        return List.of(
                toDataStatus(SeedVersion.BOUNDARIES, wardRepository.count()),
                toDataStatus(SeedVersion.RESCUE_STATIONS, rescueStationRepository.count()));
    }

    private DataStatusResponse toDataStatus(String dataset, long total) {
        Optional<SeedVersion> seed = seedVersionRepository.findById(dataset);
        return DataStatusResponse.builder()
                .dataset(dataset)
                .version(seed.map(SeedVersion::getVersion).orElse(null))
                .updatedAt(seed.map(SeedVersion::getAppliedAt).orElse(null))
                .checkedAt(seed.map(SeedVersion::getCheckedAt).orElse(null))
                .total(total)
                .build();
    }
}
