package com.sosvietnam.service;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;

import java.util.List;
import java.util.Map;

public interface GeoService {
    List<Province> getAllProvinces();

    List<Ward> getWardsByProvince(String provinceCode);

    Map<String, Object> resolveLocation(double lat, double lng);

    List<RescueStation> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit);
}
