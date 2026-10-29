package com.sosvietnam.repository;

import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface RescueStationRepository extends JpaRepository<RescueStation, Long>, JpaSpecificationExecutor<RescueStation> {

    List<RescueStation> findByAgencyType(AgencyType agencyType);

    List<RescueStation> findByProvince_Code(String provinceCode);


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

    /**
     * Gán lại tỉnh / xã cho mọi trạm theo toạ độ (DataSeeder sau khi nạp file, BandoSync sau khi đổi ranh giới).
     * Tỉnh = tỉnh của xã chứa điểm (không có thì giữ tỉnh theo tên trong file).
     * Xã = xã có tên nằm trọn trong tên trạm, cùng tỉnh với điểm ("Công an phường X - Cơ sở 2" có thể đặt ở xã bên cạnh;
     * "xã An Phú" không khớp "xã An Phú Tân"), không có thì xã chứa điểm.
     * Ranh giới: cột boundary (gốc bando) nếu có, không thì geometry (rút gọn).
     */
    @Modifying
    @Query(value = """
        WITH pt AS (
            SELECT s.id AS sid, w.id AS ward_id, w.province_id
            FROM rescue_stations s
            LEFT JOIN LATERAL (
                SELECT w.id, w.province_id FROM wards w
                WHERE (w.boundary IS NOT NULL AND ST_Contains(w.boundary, s.location))
                   OR (w.boundary IS NULL AND ST_Contains(w.geometry, s.location))
                LIMIT 1) w ON true
            WHERE s.location IS NOT NULL)
        UPDATE rescue_stations s SET
            province_id = COALESCE(pt.province_id, s.province_id),
            ward_id = COALESCE(
                (SELECT w.id FROM wards w WHERE w.province_id = pt.province_id
                   AND unaccent(lower(s.name)) ~ ('(^|\\s)' || unaccent(lower(w.name)) || '\\s*($|[-,(–—])')
                 -- khớp đúng cả dấu trước ("Thạnh An" không lấy "Thanh An"), rồi tên dài hơn
                 ORDER BY (lower(s.name) LIKE '%' || lower(w.name) || '%') DESC, length(w.name) DESC LIMIT 1),
                pt.ward_id)
        FROM pt WHERE pt.sid = s.id
        """, nativeQuery = true)
    int reassignAreas();
}
