package com.sosvietnam.util;

import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * So tên tỉnh / xã do người dùng gõ (bộ lọc của admin, file seed) với bảng provinces / wards.
 * Luật quyền xem dùng khoá (mã tỉnh, id xã), chỉ chỗ nhận tên từ ngoài vào mới cần hàm này.
 */
@UtilityClass
public class AreaNames {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern PROVINCE_PREFIX = Pattern.compile("^(tp\\.?|thanh pho|tinh)\\s+");

    /** Bỏ dấu, chữ thường, gộp khoảng trắng: "Phường Tân An" -> "phuong tan an". */
    public String key(String name) {
        if (name == null || name.isBlank()) return null;
        String noMarks = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Như key() và bỏ tiền tố tỉnh: "TP. Cần Thơ" -> "can tho". */
    public String provinceKey(String name) {
        String k = key(name);
        return k == null ? null : PROVINCE_PREFIX.matcher(k).replaceFirst("");
    }
}
