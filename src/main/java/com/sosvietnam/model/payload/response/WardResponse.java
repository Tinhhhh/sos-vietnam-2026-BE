package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Xã/phường thuộc 1 tỉnh, không kèm ranh giới geometry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WardResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("malk")
    private String malk;

    @JsonProperty("name")
    private String name;

    @JsonProperty("province_code")
    private String provinceCode;

    @JsonProperty("province_name")
    private String provinceName;

    // Danh sách xã/phường cũ đã sáp nhập vào xã/phường này
    @JsonProperty("sap_nhap_tu")
    private String sapNhapTu;

    @JsonProperty("center_lat")
    private Double centerLat;

    @JsonProperty("center_lng")
    private Double centerLng;
}
