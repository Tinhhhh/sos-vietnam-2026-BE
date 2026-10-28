package com.sosvietnam.repository;

import com.sosvietnam.model.entity.Province;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProvinceRepository extends JpaRepository<Province, String> {
    
    List<Province> findAllByOrderByNameAsc();

    // Ranh giới gốc (cột boundary, V25) nếu đã đồng bộ từ bando, không thì bản rút gọn (geometry).
    // boundary không map vào entity nên liệt kê cột, không SELECT p.* (khỏi kéo polygon nặng về).
    @Query(value = """
            SELECT p.code, p.name, p.name_en, p.center_lat, p.center_lng, p.geometry FROM provinces p
            WHERE (p.boundary IS NOT NULL AND ST_Contains(p.boundary, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))
               OR (p.boundary IS NULL AND ST_Contains(p.geometry, ST_SetSRID(ST_MakePoint(:lng, :lat), 4326)))
            LIMIT 1
            """, nativeQuery = true)
    Optional<Province> findProvinceContaining(@Param("lng") double lng, @Param("lat") double lat);

    default Optional<Province> findProvinceContainingPoint(Point point) {
        return findProvinceContaining(point.getX(), point.getY());
    }
}
