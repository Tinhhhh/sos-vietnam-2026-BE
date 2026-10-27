package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import lombok.*;

/** Body của POST /api/v1/field/position: một điểm GPS lấy từ trình duyệt của người phụ trách hiện trường. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldPositionRequest {

    @NotNull
    @DecimalMin("-90") @DecimalMax("90")
    @JsonProperty("latitude")
    private Double latitude;

    @NotNull
    @DecimalMin("-180") @DecimalMax("180")
    @JsonProperty("longitude")
    private Double longitude;

    /** km/h (trình duyệt cho m/s, FE đổi sẵn); null nếu không có. */
    @PositiveOrZero
    @JsonProperty("speed_kmh")
    private Double speedKmh;

    @DecimalMin("0") @DecimalMax("360")
    @JsonProperty("heading")
    private Double heading;

    @PositiveOrZero
    @JsonProperty("accuracy_m")
    private Double accuracyM;
}
