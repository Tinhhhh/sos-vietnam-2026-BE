package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** PUT /api/v1/settings/demo: trường nào null thì giữ nguyên. */
@Data
public class DemoSettingsRequest {

    @JsonProperty("generate")
    private Boolean generate;

    @JsonProperty("hidden")
    private Boolean hidden;

    /** Tốc độ xe giả của "Mô phỏng", 5-150 km/h (áp cho lần bấm Mô phỏng sau). */
    @JsonProperty("simulation_speed_kmh")
    private Double simulationSpeedKmh;
}
