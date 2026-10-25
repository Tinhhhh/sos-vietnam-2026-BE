package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.DestinationSource;
import com.sosvietnam.model.payload.enums.RouteProvider;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.*;

/**
 * Body của PUT /api/v1/incidents/{id}/dispatch-plan: bước 1 "Điều động" của cán bộ giữ ca
 * (trạm xuất phát, điểm đến, tuyến đã tính ở FE). delegate = true khi trạm thuộc đơn vị khác (cấp xã)
 * và cần bước uỷ quyền; false = chính đơn vị mình đi, sang thẳng bước theo dõi.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchPlanRequest {

    @NotNull(message = "Chưa chọn trạm xuất phát")
    @JsonProperty("station_id")
    private Long stationId;

    @NotNull(message = "Chưa có điểm đến")
    @Valid
    @JsonProperty("destination")
    private Destination destination;

    @Valid
    @JsonProperty("route")
    private Route route;

    @JsonProperty("delegate")
    private boolean delegate;

    /** Người phụ trách hiện trường (cán bộ đơn vị mình); bắt buộc khi delegate = false (đơn vị tự đi). */
    @JsonProperty("field_member_id")
    private java.util.UUID fieldMemberId;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Destination {
        @NotNull
        @DecimalMin("-90") @DecimalMax("90")
        @JsonProperty("latitude")
        private Double latitude;

        @NotNull
        @DecimalMin("-180") @DecimalMax("180")
        @JsonProperty("longitude")
        private Double longitude;

        @Size(max = 500)
        @JsonProperty("address")
        private String address;

        @NotNull
        @JsonProperty("source")
        private DestinationSource source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Route {
        @PositiveOrZero
        @JsonProperty("distance_m")
        private Integer distanceM;

        @PositiveOrZero
        @JsonProperty("duration_s")
        private Integer durationS;

        @PositiveOrZero
        @JsonProperty("duration_traffic_s")
        private Integer durationTrafficS;

        // Encoded polyline (định dạng Google, 1e-5); tuyến dài vài trăm km vẫn dưới 100 KB
        @Size(max = 200_000)
        @JsonProperty("polyline")
        private String polyline;

        @JsonProperty("provider")
        private RouteProvider provider;
    }
}
