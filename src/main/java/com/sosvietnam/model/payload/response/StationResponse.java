package com.sosvietnam.model.payload.response;

import com.sosvietnam.model.entity.RescueStation;
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

    @JsonProperty("province_id")
    private String provinceId;

    @JsonProperty("ward_id")
    private Long wardId;

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

    /** Map tay (không dùng ModelMapper): province / ward của entity là khoá ngoại, DTO cần tên. */
    /** Tên tỉnh / xã lấy từ AreaNameCache (không nạp entity Province / Ward của trạm). */
    public static StationResponse from(RescueStation s, com.sosvietnam.util.AreaNameCache areaNames) {
        return StationResponse.builder()
                .id(s.getId())
                .name(s.getName())
                .agencyType(s.getAgencyType())
                .level(s.getLevel())
                .phone(s.getPhone())
                .address(s.getAddress())
                .province(areaNames.provinceName(s.provinceId()))
                .ward(areaNames.wardName(s.wardId()))
                .provinceId(s.provinceId())
                .wardId(s.wardId())
                .latitude(s.getLatitude())
                .longitude(s.getLongitude())
                .officer(s.getOfficer())
                .email(s.getEmail())
                .services(s.getServices())
                .facilityType(s.getFacilityType())
                .build();
    }
}
