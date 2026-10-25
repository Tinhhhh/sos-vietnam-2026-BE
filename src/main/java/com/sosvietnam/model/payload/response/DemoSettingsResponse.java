package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Trạng thái ca demo: có tự sinh không, có ẩn không, đang có bao nhiêu ca [DEMO] còn mở. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoSettingsResponse {

    @JsonProperty("generate")
    private boolean generate;

    @JsonProperty("hidden")
    private boolean hidden;

    @JsonProperty("open_count")
    private long openCount;

    @JsonProperty("updated_at")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime updatedAt;

    @JsonProperty("updated_by_name")
    private String updatedByName;
}
