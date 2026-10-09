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

    @Query("SELECT p FROM Province p WHERE ST_Contains(p.geometry, :point) = true")
    Optional<Province> findProvinceContainingPoint(@Param("point") Point point);
}
