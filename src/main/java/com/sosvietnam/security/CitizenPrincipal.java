package com.sosvietnam.security;

import java.security.Principal;

/**
 * Phiên STOMP của người báo tin (không có tài khoản): chỉ gắn với đúng 1 ca, đã kiểm citizen access token
 * lúc CONNECT. getName() = "citizen:{incidentId}" nên server gửi riêng /user/queue/... cho đúng người báo ca đó.
 */
public record CitizenPrincipal(String incidentId) implements Principal {

    public static final String PREFIX = "citizen:";

    public static String nameOf(String incidentId) {
        return PREFIX + incidentId;
    }

    @Override
    public String getName() {
        return nameOf(incidentId);
    }
}
