package com.sosvietnam.service;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.model.payload.response.DataStatusResponse;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.model.payload.response.ProvinceResponse;
import com.sosvietnam.model.payload.response.StationResponse;
import com.sosvietnam.model.payload.response.WardResponse;

import java.util.List;

/** Tra cứu địa giới hành chính (tỉnh, xã/phường) và trạm cứu hộ. */
public interface GeoService {
    List<ProvinceResponse> getAllProvinces();

    List<WardResponse> getWardsByProvince(String provinceCode);

    LocationResponse resolveLocation(double lat, double lng);

    List<StationResponse> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit);

    ListResponse getStations(int pageNo, int pageSize, String province, AgencyType agencyType, StationLevel level,
                             String sortBy, String sortDir);

    List<DataStatusResponse> getDataStatus();
}
