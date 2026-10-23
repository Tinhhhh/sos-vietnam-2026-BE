package com.sosvietnam.config;

import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.repository.RefreshTokenRepository;
import com.sosvietnam.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 2h sáng mỗi ngày xoá refresh token không còn dùng được khỏi bảng refresh_tokens (như HIV-TMSS):
 * đã thu hồi / đánh dấu hết hạn (đăng xuất, đã refresh), hoặc JWT đã quá hạn 7 ngày mà chưa ai dùng.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RefreshTokenCleanupJob {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Scheduled(cron = "0 0 2 * * *")
    @Transactional
    public void deleteUnusableRefreshTokens() {
        List<RefreshToken> dead = refreshTokenRepository.findAll().stream()
                .filter(t -> t.isRevoked() || t.isExpired() || jwtTokenProvider.isTokenExpired(t.getToken()))
                .toList();
        if (dead.isEmpty()) return;
        refreshTokenRepository.deleteAll(dead);
        log.info("[TOKENS] Đã xoá {} refresh token không còn dùng được", dead.size());
    }
}
