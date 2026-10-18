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

    /** provinces.code */
    @JsonProperty("province_id")
    private String provinceId;

    @JsonProperty("province")
    private String province;

    // Danh sách xã/phường cũ đã sáp nhập vào xã/phường này
    @JsonProperty("sap_nhap_tu")
    private String sapNhapTu;

    @JsonProperty("center_lat")
    private Double centerLat;

    @JsonProperty("center_lng")
    private Double centerLng;

    public static WardResponse from(com.sosvietnam.model.entity.Ward w) {
        return WardResponse.builder()
                .id(w.getId())
                .malk(w.getMalk())
                .name(w.getName())
                .provinceId(w.getProvince() == null ? null : w.getProvince().getCode())
                .province(w.getProvince() == null ? null : w.getProvince().getName())
                .sapNhapTu(w.getSapNhapTu())
                .centerLat(w.getCenterLat())
                .centerLng(w.getCenterLng())
                .build();
    }
}
