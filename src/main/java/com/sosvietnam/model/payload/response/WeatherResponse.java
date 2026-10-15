package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Thời tiết hiện tại tại toạ độ đơn vị trực ban (nguồn Open-Meteo). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeatherResponse {

    // °C
    @JsonProperty("temperature")
    private Double temperature;

    // Mô tả tiếng Việt theo mã WMO, vd "Có mây rải rác"
    @JsonProperty("description")
    private String description;

    @JsonProperty("is_day")
    private Boolean isDay;

    // km/h
    @JsonProperty("wind_speed")
    private Double windSpeed;
}
