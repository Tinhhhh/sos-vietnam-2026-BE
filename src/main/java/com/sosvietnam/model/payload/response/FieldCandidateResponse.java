package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.UnitRole;
import lombok.*;

import java.util.UUID;

/**
 * Cán bộ của đơn vị đi xử lý, để chọn người phụ trách hiện trường (không cần trực tuyến: họ có thể đang làm
 * việc ngoài trụ sở). selectable = false khi chưa có số hiệu (mã thiết bị GPS) hoặc đang phụ trách lượt khác.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldCandidateResponse {

    @JsonProperty("member_id")
    private UUID memberId;

    /** Cấp bậc + họ tên */
    @JsonProperty("full_name")
    private String fullName;

    @JsonProperty("badge_number")
    private String badgeNumber;

    @JsonProperty("position")
    private String position;

    @JsonProperty("unit_role")
    private UnitRole unitRole;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("online")
    private boolean online;

    @JsonProperty("is_me")
    private boolean me;

    @JsonProperty("selectable")
    private boolean selectable;

    /** Lý do không chọn được, vd "Đang phụ trách ca SOS-..." / "Chưa có số hiệu" */
    @JsonProperty("unavailable_reason")
    private String unavailableReason;
}
