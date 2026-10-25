package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.PoliceRank;
import com.sosvietnam.model.payload.enums.UnitRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** Body của POST / PUT /api/v1/unit/members (chỉ huy hoặc trực ban thêm / sửa cán bộ của đơn vị). */
@Data
public class UnitMemberRequest {

    @NotBlank(message = "Nhập họ tên")
    @Size(max = 150, message = "Họ tên tối đa 150 ký tự")
    @JsonProperty("full_name")
    private String fullName;

    @Size(max = 30, message = "Số hiệu tối đa 30 ký tự")
    @JsonProperty("badge_number")
    private String badgeNumber;

    @JsonProperty("rank")
    private PoliceRank rank;

    @Size(max = 150, message = "Chức vụ tối đa 150 ký tự")
    @JsonProperty("position")
    private String position;

    @NotNull(message = "Chọn vai trò trong đơn vị")
    @JsonProperty("unit_role")
    private UnitRole unitRole;

    @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại gồm 10 số, bắt đầu bằng 0")
    @JsonProperty("phone")
    private String phone;
}
