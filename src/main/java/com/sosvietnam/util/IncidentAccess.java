package com.sosvietnam.util;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.security.OfficerPrincipal;
import lombok.experimental.UtilityClass;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Luật "ai thấy ca nào, ai được nhận ca nào" (module 5), dùng chung cho REST và STOMP.
 * - Lực lượng khác nhau không thấy ca của nhau. ADMIN thấy tất cả.
 * - Trực ban cấp tỉnh thấy mọi ca của lực lượng mình trong tỉnh.
 * - Trực ban cấp xã thấy ca xuất phát từ xã mình (kể cả ca đã leo thang, chỉ để theo dõi).
 * - Được nhận ca khi cấp của mình đúng cấp đang giữ ca. ADMIN chỉ nhận ca cấp quốc gia (không xác định được tỉnh).
 * Tên tỉnh / xã trong tài khoản và trong sự cố so sánh sau khi chuẩn hoá (bỏ dấu, hoa thường, tiền tố "TP.", "Tỉnh").
 */
@UtilityClass
public class IncidentAccess {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern PROVINCE_PREFIX = Pattern.compile("^(tp\\.?|thanh pho|tinh)\\s+");

    public boolean canView(OfficerPrincipal officer, Incident incident) {
        if (officer.isAdmin()) return true;
        if (officer.agency() == null || officer.agency() != incident.getRoutedAgency()) return false;
        if (officer.level() == UnitLevel.PROVINCE) {
            return sameProvince(officer.province(), incident.getProvince());
        }
        if (officer.level() == UnitLevel.WARD) {
            // Ca xuất phát ở cấp xã thì có hạn leo thang; ca vào thẳng tỉnh không hiện ở xã
            return incident.getEscalateAt() != null
                    && sameProvince(officer.province(), incident.getProvince())
                    && sameWard(officer.ward(), incident.getWard());
        }
        return false;
    }

    public boolean canHandle(OfficerPrincipal officer, Incident incident) {
        if (!canView(officer, incident)) return false;
        if (officer.isAdmin()) return incident.getRoutedLevel() == UnitLevel.NATIONAL;
        return officer.level() == incident.getRoutedLevel();
    }

    public boolean sameProvince(String a, String b) {
        String ka = provinceKey(a);
        return ka != null && ka.equals(provinceKey(b));
    }

    public boolean sameWard(String a, String b) {
        String ka = key(a);
        return ka != null && ka.equals(key(b));
    }

    /** "TP. Cần Thơ" -> "can tho". */
    public String provinceKey(String name) {
        String k = key(name);
        return k == null ? null : PROVINCE_PREFIX.matcher(k).replaceFirst("");
    }

    /** Bỏ dấu, chữ thường, gộp khoảng trắng: "Phường Tân An" -> "phuong tan an". */
    public String key(String name) {
        if (name == null || name.isBlank()) return null;
        String noMarks = MARKS.matcher(Normalizer.normalize(name, Normalizer.Form.NFD)).replaceAll("");
        return noMarks.replace('đ', 'd').replace('Đ', 'D').toLowerCase(Locale.ROOT).trim().replaceAll("\\s+", " ");
    }

    /** Hai trực ban cùng đơn vị (cùng lực lượng, cấp, tỉnh; cấp xã thì cùng xã): được giao ca cho nhau. */
    public boolean sameUnit(OfficerPrincipal a, OfficerPrincipal b) {
        return a.agency() != null && a.agency() == b.agency() && a.level() == b.level()
                && sameProvince(a.province(), b.province())
                && (a.level() != UnitLevel.WARD || sameWard(a.ward(), b.ward()));
    }
}
