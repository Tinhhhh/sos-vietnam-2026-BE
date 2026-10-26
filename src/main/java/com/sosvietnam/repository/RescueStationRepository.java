package com.sosvietnam.repository;

import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RescueStationRepository extends JpaRepository<RescueStation, Long>, JpaSpecificationExecutor<RescueStation> {

    List<RescueStation> findByAgencyType(AgencyType agencyType);

    List<RescueStation> findByProvince_Code(String provinceCode);

    /**
     * Xã của trạm cấp xã (phường / xã như nhau) gần điểm nhất, cùng lực lượng, trong tỉnh: dùng để giao ca công an
     * cho đơn vị có trạm gần hiện trường nhất (một xã có thể có nhiều trụ sở / cơ sở). null nếu tỉnh không có trạm.
     */
    @Query(value = """
        SELECT s.ward_id FROM rescue_stations s
        WHERE s.agency_type = :agencyType AND s.level = 'WARD' AND s.province_id = :provinceId
          AND s.ward_id IS NOT NULL AND s.location IS NOT NULL
        ORDER BY s.location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
        LIMIT 1
        """, nativeQuery = true)
    Long findNearestWardStationWardId(@Param("agencyType") String agencyType, @Param("provinceId") String provinceId,
                                      @Param("lat") double lat, @Param("lng") double lng);

    @Query(value = """
        SELECT * FROM rescue_stations s
        WHERE (:agencyType IS NULL OR s.agency_type = :agencyType)
          AND ST_DWithin(s.location::geography, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)::geography, :radiusMeters)
        ORDER BY s.location <-> ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)
        LIMIT :limitCount
        """, nativeQuery = true)
    List<RescueStation> findNearestStations(
        @Param("lat") double lat,
        @Param("lng") double lng,
        @Param("agencyType") String agencyType,
        @Param("radiusMeters") double radiusMeters,
        @Param("limitCount") int limitCount
    );
}
