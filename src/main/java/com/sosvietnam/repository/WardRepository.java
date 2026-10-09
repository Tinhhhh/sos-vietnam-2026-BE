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

    List<Ward> findByProvinceCode(String provinceCode);

    Optional<Ward> findByMalk(String malk);

    @Query("SELECT w FROM Ward w WHERE ST_Contains(w.geometry, :point) = true")
    Optional<Ward> findWardContainingPoint(@Param("point") Point point);
}
