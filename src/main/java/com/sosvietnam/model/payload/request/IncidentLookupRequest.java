package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Body của POST /api/v1/incidents/lookup (người dân tra cứu lại ca đã báo). Đi trong body, không đưa SĐT lên URL. */
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentLookupRequest {

    @Schema(description = "Mã tra cứu nhận khi ca hoàn tất", example = "K7M2-QX9P")
    @NotBlank(message = "Vui lòng nhập mã tra cứu")
    @JsonProperty("lookup_code")
    private String lookupCode;

    @Schema(description = "4 số cuối số điện thoại đã dùng khi báo tin", example = "5678")
    @NotBlank(message = "Vui lòng nhập 4 số cuối số điện thoại")
    @Pattern(regexp = "^[0-9]{4}$", message = "Nhập đúng 4 số cuối số điện thoại")
    @JsonProperty("phone_last4")
    private String phoneLast4;
}
