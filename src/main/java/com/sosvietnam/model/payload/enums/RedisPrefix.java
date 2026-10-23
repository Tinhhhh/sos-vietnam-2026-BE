package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/**
 * Tiền tố key Redis (giống HIV-TMSS). Key = prefix + "_" + ...; TTL của key = thời gian còn cần nhớ.
 */
@Getter
public enum RedisPrefix {
    TOKEN_BLACKLIST("TOKEN_BLACKLIST", "Access token đã thu hồi (đăng xuất / refresh): key _{userId}_{jwtId}"),
    TOKEN_IAT_AVAILABLE("TOKEN_IAT_AVAILABLE", "Mốc đá văng mọi phiên (đổi mật khẩu): key _{userId}, value = epoch ms");

    private final String prefix;
    private final String description;

    RedisPrefix(String prefix, String description) {
        this.prefix = prefix;
        this.description = description;
    }

    public String key(Object... parts) {
        StringBuilder sb = new StringBuilder(prefix);
        for (Object p : parts) sb.append('_').append(p);
        return sb.toString();
    }
}
