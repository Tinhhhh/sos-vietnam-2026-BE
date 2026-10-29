package com.sosvietnam.repository;

import com.sosvietnam.model.entity.Ward;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface WardRepository extends JpaRepository<Ward, Long> {

    List<Ward> findByProvince_Code(String provinceCode);

    List<Ward> findByProvince_CodeOrderByNameAsc(String provinceCode);

    List<Ward> findByProvince_CodeAndNameIgnoreCase(String provinceCode, String name);

    Optional<Ward> findByMalk(String malk);

    // Ranh giới gốc (cột boundary, V25) nếu đã đồng bộ từ bando, không thì bản rút gọn (geometry).
    // boundary không map vào entity nên liệt kê cột, không SELECT w.* (khỏi kéo polygon nặng về).
    @Query(value = """
            SELECT w.id, w.malk, w.name, w.province_id, w.sap_nhap_tu, w.center_lat, w.center_lng, w.geometry,
                   w.last_synced_at
            FROM wards w
            WHERE (w.boundary IS NOT NULL AND ST_Contains(w.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))
               OR (w.boundary IS NULL AND ST_Contains(w.geometry, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Ward> findWardContaining(@Param("lng") double lng, @Param("lat") double lat);

    default Optional<Ward> findWardContainingPoint(Point point) {
        return findWardContaining(point.getX(), point.getY());
    }
}
