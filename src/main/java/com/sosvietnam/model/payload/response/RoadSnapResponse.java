package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Điểm xe dừng gợi ý cho một vị trí SOS (dân trong hẻm, xe khó vào): điểm gần nhất trên đường có tên, không phải
 * hẻm / kiệt / ngõ / ngách trong bán kính tìm, không có thì điểm gần nhất xe vào được. source: MAIN_ROAD | NEAREST_ROAD
 * | NONE (không tìm được, giữ điểm của dân).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoadSnapResponse {

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("road_name")
    private String roadName;

    /** Khoảng cách từ vị trí của dân tới điểm xe dừng, mét. */
    @JsonProperty("distance_m")
    private Integer distanceM;

    @JsonProperty("source")
    private String source;
}
