package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.PoliceRank;
import com.sosvietnam.model.payload.enums.UnitRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Một cán bộ của đơn vị (màn chọn cán bộ lúc vào trực, Cài đặt, /auth/me). Không bao giờ có PIN. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UnitMemberResponse {

    @JsonProperty("member_id")
    private UUID memberId;

    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("badge_number")
    private String badgeNumber;

    @JsonProperty("rank")
    private PoliceRank rank;

    @JsonProperty("rank_label")
    private String rankLabel;

    @JsonProperty("position")
    private String position;

    @JsonProperty("unit_role")
    private UnitRole unitRole;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("activated")
    private boolean activated;

    @JsonProperty("locked")
    private boolean locked;

    @JsonProperty("is_duty_officer")
    private boolean dutyOfficer;

    public static UnitMemberResponse from(UnitMember m, boolean dutyOfficer) {
        return UnitMemberResponse.builder()
                .memberId(m.getId())
                .fullName(m.getFullName())
                .badgeNumber(m.getBadgeNumber())
                .rank(m.getRank())
                .rankLabel(m.getRank() == null ? null : m.getRank().getLabel())
                .position(m.getPosition())
                .unitRole(m.getUnitRole())
                .phone(m.getPhone())
                .activated(m.isActivated())
                .locked(m.isLocked())
                .dutyOfficer(dutyOfficer)
                .build();
    }

    /** Màn chọn cán bộ lúc đăng nhập (chưa nhập PIN): không trả SĐT. */
    public static UnitMemberResponse forLogin(UnitMember m) {
        UnitMemberResponse r = from(m, false);
        r.setPhone(null);
        return r;
    }
}
