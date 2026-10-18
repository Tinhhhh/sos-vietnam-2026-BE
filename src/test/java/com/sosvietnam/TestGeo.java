package com.sosvietnam;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.util.AreaNames;

import java.util.List;
import java.util.UUID;

/**
 * Tỉnh / xã dùng trong test (địa bàn giờ là khoá ngoại). Cho phép viết test bằng tên như trước:
 * province("TP. Cần Thơ") / ward("Phường Tân An") trả về đúng entity; tên lạ / placeholder trả về null.
 */
public final class TestGeo {

    public static final Province CAN_THO = Province.builder().code("92").name("Cần Thơ").build();
    public static final Province HA_NOI = Province.builder().code("01").name("Hà Nội").build();
    public static final Province HCM = Province.builder().code("79").name("Hồ Chí Minh").build();

    public static final Ward TAN_AN = Ward.builder().id(9201L).name("Phường Tân An").province(CAN_THO).build();
    public static final Ward CAI_KHE = Ward.builder().id(9202L).name("Phường Cái Khế").province(CAN_THO).build();
    public static final Ward NINH_KIEU = Ward.builder().id(9203L).name("Phường Ninh Kiều").province(CAN_THO).build();
    public static final Ward HOAN_KIEM = Ward.builder().id(101L).name("Phường Hoàn Kiếm").province(HA_NOI).build();
    public static final Ward BA_DINH = Ward.builder().id(102L).name("Phường Ba Đình").province(HA_NOI).build();

    private static final List<Province> PROVINCES = List.of(CAN_THO, HA_NOI, HCM);
    private static final List<Ward> WARDS = List.of(TAN_AN, CAI_KHE, NINH_KIEU, HOAN_KIEM, BA_DINH);

    private TestGeo() {
    }

    public static List<Province> provinces() {
        return PROVINCES;
    }

    public static Province province(String name) {
        String key = AreaNames.provinceKey(name);
        return key == null ? null
                : PROVINCES.stream().filter(p -> key.equals(AreaNames.provinceKey(p.getName()))).findFirst().orElse(null);
    }

    public static Ward ward(String name) {
        String key = AreaNames.key(name);
        return key == null ? null : WARDS.stream().filter(w -> key.equals(AreaNames.key(w.getName()))).findFirst().orElse(null);
    }

    /** OfficerPrincipal từ tên tỉnh / xã như cách viết test cũ. */
    public static OfficerPrincipal officer(UUID id, String email, String fullName, RoleName role,
                                           AgencyType agency, UnitLevel level, String provinceName, String wardName) {
        Province p = province(provinceName);
        Ward w = ward(wardName);
        return new OfficerPrincipal(id, email, fullName, role, agency, level,
                p == null ? null : p.getCode(), p == null ? null : p.getName(),
                w == null ? null : w.getId(), w == null ? null : w.getName());
    }
}
