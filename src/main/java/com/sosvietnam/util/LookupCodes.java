package com.sosvietnam.util;

import java.security.SecureRandom;
import java.util.Locale;

/**
 * Mã tra cứu ca cho người dân: 8 ký tự từ bảng chữ dễ đọc (bỏ 0/O, 1/I/L), lưu liền, hiển thị XXXX-XXXX.
 * Không suy ra được incidents.id; tra cứu luôn đi kèm 4 số cuối SĐT người báo.
 */
public final class LookupCodes {

    public static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    public static final int LENGTH = 8;
    private static final SecureRandom RANDOM = new SecureRandom();

    private LookupCodes() {
    }

    public static String generate() {
        StringBuilder sb = new StringBuilder(LENGTH);
        for (int i = 0; i < LENGTH; i++) {
            sb.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    /** "k7m2-qx9p", " K7M2 QX9P " -> "K7M2QX9P"; null nếu sai định dạng. */
    public static String normalize(String input) {
        if (input == null) return null;
        String code = input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
        if (code.length() != LENGTH) return null;
        for (char c : code.toCharArray()) {
            if (ALPHABET.indexOf(c) < 0) return null;
        }
        return code;
    }

    /** "K7M2QX9P" -> "K7M2-QX9P" */
    public static String format(String code) {
        return code == null || code.length() != LENGTH ? code : code.substring(0, 4) + "-" + code.substring(4);
    }
}
