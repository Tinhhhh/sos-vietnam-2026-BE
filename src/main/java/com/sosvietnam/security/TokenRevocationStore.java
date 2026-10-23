package com.sosvietnam.security;

import com.sosvietnam.model.payload.enums.RedisPrefix;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Access token đã thu hồi + mốc "đá văng mọi phiên" của từng tài khoản (cơ chế của HIV-TMSS).
 *
 * Đọc ở RAM, ghi cả RAM lẫn Redis: mỗi request kiểm tra token không phải hỏi Redis (BE chạy 1 instance),
 * Redis chỉ để BE khởi động lại vẫn nhớ (lúc khởi động nạp lại từ Redis). Không có REDIS_URL hoặc Redis lỗi
 * thì chạy bằng RAM như trước, chỉ ghi log cảnh báo. Chạy 2 BE trở lên thì phải chuyển sang đọc thẳng Redis.
 */
@Component
@Slf4j
public class TokenRevocationStore {

    /** userId_jwtId -> hết hạn (epoch ms) */
    private final Map<String, Long> revoked = new ConcurrentHashMap<>();
    /** userId -> {mốc đá văng (epoch ms), hết hạn (epoch ms)} */
    private final Map<UUID, long[]> logoutAll = new ConcurrentHashMap<>();

    private final StringRedisTemplate redis;

    public TokenRevocationStore(ObjectProvider<StringRedisTemplate> redisProvider,
                                @Value("${app.redis.url:}") String redisUrl) {
        this.redis = StringUtils.hasText(redisUrl) ? redisProvider.getIfAvailable() : null;
    }

    public boolean usesRedis() {
        return redis != null;
    }

    /** Nạp lại những gì còn hạn trong Redis (BE vừa khởi động lại). */
    @PostConstruct
    void loadFromRedis() {
        if (redis == null) {
            log.info("[TOKENS] Không có REDIS_URL: token thu hồi chỉ lưu trong RAM (khởi động lại là mất)");
            return;
        }
        try {
            long now = System.currentTimeMillis();
            int tokens = 0;
            for (String key : scan(RedisPrefix.TOKEN_BLACKLIST.getPrefix() + "_*")) {
                Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
                if (ttl == null || ttl <= 0) continue;
                revoked.put(key.substring(RedisPrefix.TOKEN_BLACKLIST.getPrefix().length() + 1), now + ttl);
                tokens++;
            }
            int marks = 0;
            for (String key : scan(RedisPrefix.TOKEN_IAT_AVAILABLE.getPrefix() + "_*")) {
                Long ttl = redis.getExpire(key, TimeUnit.MILLISECONDS);
                String value = redis.opsForValue().get(key);
                if (ttl == null || ttl <= 0 || value == null) continue;
                UUID userId = UUID.fromString(key.substring(RedisPrefix.TOKEN_IAT_AVAILABLE.getPrefix().length() + 1));
                logoutAll.put(userId, new long[]{Long.parseLong(value), now + ttl});
                marks++;
            }
            log.info("[TOKENS] Nạp từ Redis: {} token đã thu hồi, {} mốc đá văng", tokens, marks);
        } catch (RuntimeException e) {
            log.warn("[TOKENS] Không đọc được Redis, chạy bằng RAM: {}", e.getMessage());
        }
    }

    private List<String> scan(String pattern) {
        List<String> keys = new ArrayList<>();
        redis.execute((RedisCallback<Void>) connection -> {
            scanInto(connection, pattern, keys);
            return null;
        });
        return keys;
    }

    private static void scanInto(RedisConnection connection, String pattern, List<String> keys) {
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(500).build();
        try (Cursor<byte[]> cursor = connection.keyCommands().scan(options)) {
            cursor.forEachRemaining(k -> keys.add(new String(k, StandardCharsets.UTF_8)));
        }
    }

    // ---------------------------------------------------------------- access token đã thu hồi

    public void revoke(UUID userId, UUID jwtId, long ttlMillis) {
        if (userId == null || jwtId == null || ttlMillis <= 0) return;
        String id = userId + "_" + jwtId;
        revoked.put(id, System.currentTimeMillis() + ttlMillis);
        write(RedisPrefix.TOKEN_BLACKLIST.key(id), "1", ttlMillis);
    }

    public boolean isRevoked(UUID userId, UUID jwtId) {
        if (userId == null || jwtId == null) return false;
        String id = userId + "_" + jwtId;
        Long expiry = revoked.get(id);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            revoked.remove(id);
            return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- đá văng mọi phiên

    /** Mọi token (access lẫn refresh) của tài khoản cấp trước lúc này đều hết dùng được. */
    public void logoutEverywhere(UUID userId, long ttlMillis) {
        if (userId == null || ttlMillis <= 0) return;
        long now = System.currentTimeMillis();
        logoutAll.put(userId, new long[]{now, now + ttlMillis});
        write(RedisPrefix.TOKEN_IAT_AVAILABLE.key(userId), Long.toString(now), ttlMillis);
    }

    /**
     * Token cấp trước mốc đá văng? So theo giây vì iat của JWT chỉ có tới giây
     * (token cấp ngay trong giây đổi mật khẩu, tức lần đăng nhập lại, vẫn dùng được).
     */
    public boolean issuedBeforeLogoutAll(UUID userId, Date issuedAt) {
        if (userId == null || issuedAt == null) return false;
        long[] mark = logoutAll.get(userId);
        if (mark == null) return false;
        if (System.currentTimeMillis() > mark[1]) {
            logoutAll.remove(userId);
            return false;
        }
        return issuedAt.getTime() / 1000 < mark[0] / 1000;
    }

    // ----------------------------------------------------------------

    private void write(String key, String value, long ttlMillis) {
        if (redis == null) return;
        try {
            redis.opsForValue().set(key, value, Duration.ofMillis(ttlMillis));
        } catch (RuntimeException e) {
            // RAM đã ghi, request vẫn đúng; chỉ là BE khởi động lại sẽ quên key này
            log.warn("[TOKENS] Không ghi được Redis key {}: {}", key, e.getMessage());
        }
    }

    /** Dọn những mục đã hết hạn khỏi RAM (Redis tự xoá theo TTL). */
    @Scheduled(fixedDelay = 3_600_000L)
    void purgeExpired() {
        long now = System.currentTimeMillis();
        revoked.values().removeIf(expiry -> expiry < now);
        logoutAll.values().removeIf(mark -> mark[1] < now);
    }
}
