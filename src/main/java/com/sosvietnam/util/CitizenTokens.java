package com.sosvietnam.util;

import com.sosvietnam.model.entity.Incident;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Mã truy cập của người báo tin (citizen access token): trả về 1 lần lúc tạo ca, DB chỉ lưu SHA-256.
 * Dùng chung cho REST (header X-SOS-Access-Token) và STOMP (frame CONNECT).
 */
public final class CitizenTokens {

    private CitizenTokens() {
    }

    public static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** So sánh thời gian hằng (không lộ độ dài khớp qua thời gian phản hồi). */
    public static boolean matches(Incident incident, String token) {
        if (incident == null || token == null || token.isBlank()) return false;
        String expected = incident.getCitizenTokenHash();
        return expected != null && MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.US_ASCII), sha256(token).getBytes(StandardCharsets.US_ASCII));
    }
}
