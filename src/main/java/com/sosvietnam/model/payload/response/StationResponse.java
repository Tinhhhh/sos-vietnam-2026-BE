package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Trạm cứu hộ (công an, PCCC, bệnh viện...), không kèm geometry. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StationResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("name")
    private String name;

    @JsonProperty("agency_type")
    private AgencyType agencyType;

    @JsonProperty("level")
    private StationLevel level;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("address")
    private String address;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("officer")
    private String officer;

    @JsonProperty("email")
    private String email;

    @JsonProperty("services")
    private String services;

    @JsonProperty("facility_type")
    private String facilityType;
}
