package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body của POST /api/v1/admin/accounts (ADMIN tạo tài khoản cán bộ / quản trị).
 * address chỉ giữ phần chi tiết (số nhà, đường, khu vực); tên xã/tỉnh lấy từ ward_id/province_id.
 */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NewAccountRequest {

    @Schema(description = "Họ và tên đệm", example = "Nguyễn Văn")
    @NotBlank(message = "Họ không được để trống")
    @JsonProperty("last_name")
    @Pattern(regexp = "^[\\p{L}\\s]*$", message = "Họ chỉ được chứa chữ cái")
    @Size(max = 100, message = "Họ tối đa 100 ký tự")
    private String lastName;

    @Schema(description = "Tên", example = "An")
    @NotBlank(message = "Tên không được để trống")
    @JsonProperty("first_name")
    @Pattern(regexp = "^[\\p{L}\\s]*$", message = "Tên chỉ được chứa chữ cái")
    @Size(max = 100, message = "Tên tối đa 100 ký tự")
    private String firstName;

    @Schema(description = "Email đăng nhập", example = "congan.tanan@sos.vn")
    @NotBlank(message = "Email không được để trống")
    @Email(message = "Định dạng email không hợp lệ")
    @Size(max = 100, message = "Email tối đa 100 ký tự")
    private String email;

    @Schema(description = "Mật khẩu ban đầu, cán bộ tự đổi sau")
    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 8, max = 100, message = "Mật khẩu phải từ 8 đến 100 ký tự")
    private String password;

    @Schema(description = "Số điện thoại", example = "0912345678")
    @Pattern(regexp = "^0[0-9]{9}$", message = "Số điện thoại phải gồm 10 số, bắt đầu bằng 0")
    private String phone;

    @Schema(description = "Địa chỉ chi tiết (số nhà, đường), không ghi xã/tỉnh", example = "02 Nguyễn Tri Phương, Kv.10")
    @Size(max = 255, message = "Địa chỉ tối đa 255 ký tự")
    private String address;

    @Schema(description = "OFFICER hoặc ADMIN")
    @NotNull(message = "Vai trò không được để trống")
    @JsonProperty("role_name")
    private RoleName roleName;

    @Schema(description = "Lực lượng (bắt buộc với OFFICER)")
    @JsonProperty("agency_type")
    private AgencyType agencyType;

    @Schema(description = "Cấp đơn vị: WARD / PROVINCE (OFFICER), ADMIN mặc định NATIONAL")
    @JsonProperty("unit_level")
    private UnitLevel unitLevel;

    @Schema(description = "Mã tỉnh (provinces.code)", example = "92")
    @NotBlank(message = "Vui lòng chọn tỉnh/thành phố")
    @JsonProperty("province_id")
    private String provinceId;

    @Schema(description = "wards.id: xã của đơn vị (cấp tỉnh là xã đặt trụ sở)", example = "9201")
    @NotNull(message = "Vui lòng chọn xã/phường")
    @JsonProperty("ward_id")
    private Long wardId;
}
