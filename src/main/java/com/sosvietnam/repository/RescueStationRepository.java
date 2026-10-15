package com.sosvietnam.repository;

import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RescueStationRepository extends JpaRepository<RescueStation, Long> {

    List<RescueStation> findByAgencyType(AgencyType agencyType);

    List<RescueStation> findByProvince(String province);

    // Tham số null = không lọc theo tiêu chí đó
    @Query("""
        SELECT s FROM RescueStation s
        WHERE (:province IS NULL OR s.province = :province)
          AND (:agencyType IS NULL OR s.agencyType = :agencyType)
          AND (:level IS NULL OR s.level = :level)
        ORDER BY s.province, s.name
        """)
    List<RescueStation> findStations(
        @Param("province") String province,
        @Param("agencyType") AgencyType agencyType,
        @Param("level") StationLevel level
    );

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
