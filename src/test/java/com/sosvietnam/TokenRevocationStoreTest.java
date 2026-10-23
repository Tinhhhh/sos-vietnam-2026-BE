package com.sosvietnam;

import com.sosvietnam.security.TokenRevocationStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Date;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Token thu hồi + mốc đá văng: đọc ở RAM, ghi thêm Redis khi có REDIS_URL; Redis lỗi thì vẫn chạy bằng RAM. */
class TokenRevocationStoreTest {

    @SuppressWarnings("unchecked")
    static TokenRevocationStore memoryOnly() {
        return new TokenRevocationStore(mock(ObjectProvider.class), "");
    }

    @Test
    void revokedToken_isRejected_untilItExpires() throws Exception {
        TokenRevocationStore store = memoryOnly();
        UUID user = UUID.randomUUID();
        UUID jwt = UUID.randomUUID();
        assertThat(store.isRevoked(user, jwt)).isFalse();

        store.revoke(user, jwt, 60_000);
        assertThat(store.isRevoked(user, jwt)).isTrue();
        assertThat(store.isRevoked(user, UUID.randomUUID())).isFalse();

        UUID shortLived = UUID.randomUUID();
        store.revoke(user, shortLived, 1);
        Thread.sleep(5);
        assertThat(store.isRevoked(user, shortLived)).isFalse();
    }

    @Test
    void logoutEverywhere_blocksTokensIssuedBefore_butNotSameSecondOrLater() throws Exception {
        TokenRevocationStore store = memoryOnly();
        UUID user = UUID.randomUUID();
        Date before = new Date(System.currentTimeMillis() - 2_000);

        store.logoutEverywhere(user, 60_000);
        assertThat(store.issuedBeforeLogoutAll(user, before)).isTrue();
        assertThat(store.issuedBeforeLogoutAll(user, new Date())).isFalse();
        assertThat(store.issuedBeforeLogoutAll(UUID.randomUUID(), before)).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    void withRedis_writesKeysWithTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        TokenRevocationStore store = new TokenRevocationStore(provider, "redis://default:x@host:1");
        UUID user = UUID.randomUUID();
        UUID jwt = UUID.randomUUID();

        store.revoke(user, jwt, 86_400_000);
        store.logoutEverywhere(user, 604_800_000);

        verify(ops).set(eq("TOKEN_BLACKLIST_" + user + "_" + jwt), eq("1"), eq(Duration.ofMillis(86_400_000)));
        verify(ops).set(eq("TOKEN_IAT_AVAILABLE_" + user), anyString(), eq(Duration.ofMillis(604_800_000)));
        assertThat(store.usesRedis()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void redisDown_stillWorksFromMemory() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new org.springframework.data.redis.RedisConnectionFailureException("down"));
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(redis);
        TokenRevocationStore store = new TokenRevocationStore(provider, "redis://default:x@host:1");
        UUID user = UUID.randomUUID();
        UUID jwt = UUID.randomUUID();

        store.revoke(user, jwt, 60_000);
        assertThat(store.isRevoked(user, jwt)).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void noRedisUrl_neverTouchesRedis() {
        ObjectProvider<StringRedisTemplate> provider = mock(ObjectProvider.class);
        TokenRevocationStore store = new TokenRevocationStore(provider, "");
        store.revoke(UUID.randomUUID(), UUID.randomUUID(), 60_000);
        verify(provider, never()).getIfAvailable();
        assertThat(store.usesRedis()).isFalse();
    }
}
