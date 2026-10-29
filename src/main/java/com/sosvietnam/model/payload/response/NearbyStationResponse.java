package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.StationLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một cơ sở gần người dân (màn báo SOS: "Y tế" = bệnh viện, "Công an gần nhất" = cơ quan công an).
 * Chỉ thông tin công bố của cơ sở: tên, cấp, SĐT, địa chỉ, toạ độ; distance_m = đường chim bay từ vị trí người dân.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NearbyStationResponse {
    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("level")
    private StationLevel level;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("address")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("distance_m")
    private Integer distanceM;
}
