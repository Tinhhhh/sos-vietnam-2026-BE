package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Trạng thái ca demo: có tự sinh không, có ẩn không, đang có bao nhiêu ca [DEMO] còn mở / tổng cộng. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSettingsResponse {

    @JsonProperty("generate")
    private boolean generate;

    @JsonProperty("hidden")
    private boolean hidden;

    @JsonProperty("simulation_speed_kmh")
    private double simulationSpeedKmh;

    @JsonProperty("open_count")
    private long openCount;

    /** Mọi ca [DEMO] còn trong hệ thống (mở + đã đóng): nút "Dọn tất cả" xoá chừng này ca. */
    @JsonProperty("total_count")
    private long totalCount;

    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonProperty("updated_by_name")
    private String updatedByName;
}
