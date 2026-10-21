package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.SignatureType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Ký văn bản báo cáo. Dùng cho:
 * - POST /incidents/{id}/document/sign-officer (cán bộ ký ô của mình)
 * - POST /incidents/{id}/document/sign-proxy (cán bộ ký hộ người dân: thêm reason + authorized = true)
 * - POST /incidents/{id}/document/citizen-sign (người dân, header X-SOS-Access-Token)
 * - POST /incidents/lookup/document/sign (người dân, thêm lookup_code + phone_last4)
 * type = DRAW: signature_data là ảnh PNG dạng data URL (tối đa ~300 KB); TYPE: chỉ cần name.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SignDocumentRequest {

    @NotBlank(message = "Vui lòng nhập họ và tên người ký")
    @Size(max = 200)
    @JsonProperty("name")
    private String name;

    @NotNull(message = "Chọn cách ký: vẽ tay hoặc gõ họ tên")
    @JsonProperty("type")
    private SignatureType type;

    @Size(max = 400_000, message = "Ảnh chữ ký quá lớn")
    @Pattern(regexp = "^data:image/png;base64,[A-Za-z0-9+/=]+$", message = "Ảnh chữ ký không hợp lệ")
    @JsonProperty("signature_data")
    private String signatureData;

    /** Chỉ khi cán bộ ký hộ: lý do (5-500 ký tự) */
    @Size(max = 500)
    @JsonProperty("reason")
    private String reason;

    /** Chỉ khi cán bộ ký hộ: xác nhận đã được người dân uỷ quyền ký thay */
    @JsonProperty("authorized")
    private boolean authorized;

    /** Chỉ khi người dân ký qua mã tra cứu */
    @Size(max = 20)
    @JsonProperty("lookup_code")
    private String lookupCode;

    @Pattern(regexp = "^[0-9]{4}$", message = "Nhập đúng 4 số cuối số điện thoại")
    @JsonProperty("phone_last4")
    private String phoneLast4;
}
