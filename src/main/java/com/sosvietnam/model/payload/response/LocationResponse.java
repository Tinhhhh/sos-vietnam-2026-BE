package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả tra địa bàn từ toạ độ (GET /api/v1/geo/resolve).
 * province_id = null nghĩa là toạ độ nằm ngoài ranh giới 34 tỉnh đã có dữ liệu.
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

    /** wards.id; null khi không khớp xã nào. */
    @JsonProperty("ward_id")
    private Long wardId;

    @JsonProperty("province")
    private String province;

    /** provinces.code */
    @JsonProperty("province_id")
    private String provinceId;

    @JsonProperty("sap_nhap_tu")
    private String sapNhapTu;
}
