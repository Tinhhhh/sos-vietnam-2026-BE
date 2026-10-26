package com.sosvietnam.service;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Ward;
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
    /** resolveLocation(): điểm nằm trong tỉnh nhưng không khớp xã/phường nào. */
    String UNKNOWN_WARD = "Chưa xác định xã/phường";
    /** resolveLocation(): điểm ngoài ranh giới dữ liệu (province_code = null). */
    String OUTSIDE_BOUNDARIES = "Vùng ngoài ranh giới";

    List<ProvinceResponse> getAllProvinces();

    List<WardResponse> getWardsByProvince(String provinceId);

    LocationResponse resolveLocation(double lat, double lng);

    /** Địa bàn của một điểm dưới dạng entity (khoá ngoại cho incidents / accounts). */
    Area resolveArea(double lat, double lng);

    /**
     * @param province tỉnh chứa điểm, null = ngoài ranh giới dữ liệu
     * @param ward     xã chứa điểm, null = trong tỉnh nhưng không khớp xã nào
     */

    record Area(Province province, Ward ward) {
        public static final Area NONE = new Area(null, null);
    }

    List<StationResponse> findNearestStations(double lat, double lng, AgencyType agencyType, double radiusMeters, int limit);

    ListResponse getStations(int pageNo, int pageSize, String province, AgencyType agencyType, StationLevel level,
                             String sortBy, String sortDir);

    List<DataStatusResponse> getDataStatus();
}
