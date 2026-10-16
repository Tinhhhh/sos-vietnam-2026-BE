package com.sosvietnam.model.payload.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lực lượng nghiệp vụ, kèm các cấp đơn vị (UnitLevel) mà lực lượng đó có và cấp trực nhận ca.
 * - Công an: xã/phường trực, quá 15 phút chưa ai nhận thì leo thang lên Công an tỉnh.
 * - CSGT, PCCC: cấp Đội (WARD, phụ trách 1 hoặc nhiều xã) chỉ lưu thông tin, không trực;
 *   ca vào thẳng cấp Phòng (PROVINCE), không leo thang.
 * - Y tế: không nhận ca (người dân xem danh bạ bệnh viện và gọi trực tiếp); tài khoản để quản lý danh bạ sau này.
 */
@Getter
public enum AgencyType {
    POLICE("Công An", EnumSet.of(UnitLevel.PROVINCE, UnitLevel.WARD), UnitLevel.WARD, true),
    CSGT("Cảnh Sát Giao Thông", EnumSet.of(UnitLevel.PROVINCE, UnitLevel.WARD), UnitLevel.PROVINCE, false),
    FIRE("Cảnh Sát PCCC & CNCH", EnumSet.of(UnitLevel.PROVINCE, UnitLevel.WARD), UnitLevel.PROVINCE, false),
    HOSPITAL("Y Tế - Cấp Cứu 115", EnumSet.of(UnitLevel.PROVINCE), null, false);

    private final String label;
    private final Set<UnitLevel> levels;
    // Cấp nhận ca khi người dân gửi SOS; null = lực lượng không nhận ca
    private final UnitLevel intakeLevel;
    // Ca ở cấp xã quá hạn chưa ai nhận thì chuyển lên cấp tỉnh
    private final boolean escalates;

    AgencyType(String label, Set<UnitLevel> levels, UnitLevel intakeLevel, boolean escalates) {
        this.label = label;
        this.levels = levels;
        this.intakeLevel = intakeLevel;
        this.escalates = escalates;
    }

    public boolean supports(UnitLevel level) {
        return levels.contains(level);
    }

    public boolean receivesIncidents() {
        return intakeLevel != null;
    }

    /** Tên cấp theo lực lượng: CSGT, PCCC gọi là cấp Đội / cấp Phòng, còn lại theo UnitLevel. */
    public String levelLabel(UnitLevel level) {
        if (level == null) return "";
        if (this == CSGT || this == FIRE) {
            if (level == UnitLevel.WARD) return "Cấp Đội";
            if (level == UnitLevel.PROVINCE) return "Cấp Phòng";
        }
        return level.getLabel();
    }
}
