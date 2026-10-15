package com.sosvietnam.model.payload.enums;

import lombok.Getter;

import java.util.EnumSet;
import java.util.Set;

/**
 * Lực lượng nghiệp vụ, kèm các cấp đơn vị (UnitLevel) mà lực lượng đó có.
 * CSGT thuộc Công an nên dùng POLICE.
 */
@Getter
public enum AgencyType {
    POLICE("Công An", EnumSet.of(UnitLevel.PROVINCE, UnitLevel.WARD)),
    MOBILE_POLICE("Cảnh Sát Cơ Động", EnumSet.of(UnitLevel.PROVINCE)),
    FIRE("Cảnh Sát PCCC & CNCH", EnumSet.of(UnitLevel.PROVINCE, UnitLevel.WARD)),
    HOSPITAL("Y Tế - Cấp Cứu 115", EnumSet.of(UnitLevel.PROVINCE));

    private final String label;
    private final Set<UnitLevel> levels;

    AgencyType(String label, Set<UnitLevel> levels) {
        this.label = label;
        this.levels = levels;
    }

    public boolean supports(UnitLevel level) {
        return levels.contains(level);
    }
}
