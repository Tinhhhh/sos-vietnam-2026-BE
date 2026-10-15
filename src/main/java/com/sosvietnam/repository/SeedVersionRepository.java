package com.sosvietnam.repository;

import com.sosvietnam.model.entity.SeedVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SeedVersionRepository extends JpaRepository<SeedVersion, String> {
}
