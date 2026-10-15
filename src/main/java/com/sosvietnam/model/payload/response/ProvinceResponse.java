package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tỉnh/thành (34 tỉnh sau sáp nhập), không kèm ranh giới geometry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvinceResponse {

    @JsonProperty("code")
    private String code;

    @JsonProperty("name")
    private String name;

    @JsonProperty("name_en")
    private String nameEn;

    @JsonProperty("center_lat")
    private Double centerLat;

    @JsonProperty("center_lng")
    private Double centerLng;
}
