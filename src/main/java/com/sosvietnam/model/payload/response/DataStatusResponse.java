package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Phiên bản một bộ dữ liệu bản đồ (boundaries | rescue_stations). version/updated_at null = chưa nạp lần nào. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataStatusResponse {

    @JsonProperty("dataset")
    private String dataset;

    @JsonProperty("version")
    private String version;

    @JsonProperty("updated_at")
    private Instant updatedAt;

    @JsonProperty("checked_at")
    private Instant checkedAt;

    @JsonProperty("total")
    private long total;
}
