package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class GeoServiceImpl implements GeoService {

    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final RescueStationRepository rescueStationRepository;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Override
    public List<Province> getAllProvinces() {
        return provinceRepository.findAllByOrderByNameAsc();
    }

    @Override
    public List<Ward> getWardsByProvince(String provinceCode) {
        return wardRepository.findByProvinceCode(provinceCode);
    }

    @Override
    public Map<String, Object> resolveLocation(double lat, double lng) {
        Point point = geometryFactory.createPoint(new Coordinate(lng, lat));
        point.setSRID(4326);

        Map<String, Object> result = new HashMap<>();
        result.put("latitude", lat);
        result.put("longitude", lng);

        Optional<Ward> wardOpt = wardRepository.findWardContainingPoint(point);
        if (wardOpt.isPresent()) {
            Ward ward = wardOpt.get();
            result.put("ward", ward.getName());
            result.put("province", ward.getProvinceName());
            result.put("provinceCode", ward.getProvinceCode());
            result.put("sapNhapTu", ward.getSapNhapTu());
        } else {
            Optional<Province> provOpt = provinceRepository.findProvinceContainingPoint(point);
            if (provOpt.isPresent()) {
                Province prov = provOpt.get();
                result.put("province", prov.getName());
                result.put("provinceCode", prov.getCode());
                result.put("ward", "Chưa xác định xã/phường");
            } else {
                result.put("province", "Việt Nam");
                result.put("ward", "Vùng ngoài ranh giới");
            }
        }
        return result;
    }

    @Override
    public List<RescueStation> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit) {
        String agencyStr = agencyType != null ? agencyType.name() : null;
        return rescueStationRepository.findNearestStations(lat, lng, agencyStr, radiusMeters, limit);
    }
}
