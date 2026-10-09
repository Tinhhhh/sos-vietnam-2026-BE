package com.sosvietnam.repository;

import com.sosvietnam.model.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String refreshToken);

    List<RefreshToken> findAllByExpiredOrRevoked(boolean expired, boolean revoked);

    RefreshToken findByJitId(UUID jitId);

    boolean existsByToken(String refreshToken);
}