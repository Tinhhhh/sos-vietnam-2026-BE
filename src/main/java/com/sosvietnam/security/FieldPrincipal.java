package com.sosvietnam.security;

import java.security.Principal;

/**
 * Phiên STOMP của người phụ trách hiện trường mở link riêng (không đăng nhập): gắn với đúng 1 lượt điều động,
 * đã kiểm field token lúc CONNECT. getName() = "field:{dispatchId}" để server gửi riêng /user/queue/calls.
 */
public record FieldPrincipal(String incidentId, Long dispatchId) implements Principal {

    public static final String PREFIX = "field:";

    public static String nameOf(Long dispatchId) {
        return PREFIX + dispatchId;
    }

    @Override
    public String getName() {
        return nameOf(dispatchId);
    }
}
