package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả tra địa bàn từ toạ độ (GET /api/v1/geo/resolve).
 * province_code = null nghĩa là toạ độ nằm ngoài ranh giới 34 tỉnh đã có dữ liệu.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationResponse {

    @JsonProperty("latitude")
    private double latitude;

    @JsonProperty("longitude")
    private double longitude;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("province")
    private String province;

    @JsonProperty("province_code")
    private String provinceCode;

    @JsonProperty("sap_nhap_tu")
    private String sapNhapTu;
}
