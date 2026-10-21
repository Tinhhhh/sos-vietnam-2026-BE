package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.*;
import lombok.*;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Kế hoạch điều động hiện hành của một ca (lượt chủ trì + tuyến đang dùng), cho cửa sổ điều động của cán bộ
 * và màn theo dõi của người báo tin. Không có tên / tài khoản cán bộ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchPlanResponse {

    @JsonProperty("dispatch_id")
    private Long dispatchId;

    @JsonProperty("status")
    private DispatchStatus status;

    @JsonProperty("status_label")
    private String statusLabel;

    /** Bước trên stepper: 1 Điều động, 2 Uỷ quyền, 3 Theo dõi, 4 Kết thúc */
    @JsonProperty("step")
    private int step;

    @JsonProperty("needs_delegation")
    private boolean needsDelegation;

    @JsonProperty("station")
    private Station station;

    /** Cán bộ đơn vị được uỷ quyền (null = chưa liên hệ, hoặc chính đơn vị mình đi) */
    @JsonProperty("assigned_to")
    private Officer assignedTo;

    @JsonProperty("reject_reason")
    private String rejectReason;

    @JsonProperty("responded_at")
    private OffsetDateTime respondedAt;

    /** Bước 4: đơn vị đi xử lý và người dân đều phải xác nhận "đã xử lý xong" trước khi lập văn bản */
    @JsonProperty("officer_confirmed_at")
    private OffsetDateTime officerConfirmedAt;

    @JsonProperty("citizen_confirmed_at")
    private OffsetDateTime citizenConfirmedAt;

    @JsonProperty("destination")
    private Point destination;

    @JsonProperty("route")
    private Route route;

    @JsonProperty("dispatched_at")
    private OffsetDateTime dispatchedAt;

    @JsonProperty("departed_at")
    private OffsetDateTime departedAt;

    @JsonProperty("arrived_at")
    private OffsetDateTime arrivedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Station {
        @JsonProperty("id")
        private Long id;
        @JsonProperty("name")
        private String name;
        @JsonProperty("agency_type")
        private AgencyType agencyType;
        @JsonProperty("level")
        private StationLevel level;
        @JsonProperty("address")
        private String address;
        @JsonProperty("phone")
        private String phone;
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Officer {
        @JsonProperty("account_id")
        private java.util.UUID accountId;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("phone")
        private String phone;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Point {
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;
        @JsonProperty("address")
        private String address;
        @JsonProperty("source")
        private DestinationSource source;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Route {
        @JsonProperty("origin")
        private Point origin;
        @JsonProperty("distance_m")
        private Integer distanceM;
        @JsonProperty("duration_s")
        private Integer durationS;
        @JsonProperty("duration_traffic_s")
        private Integer durationTrafficS;
        @JsonProperty("polyline")
        private String polyline;
        @JsonProperty("provider")
        private RouteProvider provider;
        @JsonProperty("computed_at")
        private OffsetDateTime computedAt;
    }

    /** @param route tuyến đang dùng, null nếu chưa có */
    public static DispatchPlanResponse of(IncidentDispatch d, IncidentRoute route) {
        RescueStation s = d.getStation();
        DispatchPlanResponse.Point destination = route == null ? null : Point.builder()
                .latitude(route.getDestLat()).longitude(route.getDestLng())
                .address(route.getDestAddress()).source(route.getDestSource()).build();
        return DispatchPlanResponse.builder()
                .dispatchId(d.getId())
                .status(d.getStatus())
                .statusLabel(d.getStatus().getLabel())
                .step(d.getStatus().getStep())
                .needsDelegation(d.getStatus() == DispatchStatus.PENDING_ACCEPT || d.getStatus() == DispatchStatus.ACCEPTED)
                .assignedTo(d.getAssignedTo() == null ? null : Officer.builder()
                        .accountId(d.getAssignedTo().getId())
                        .fullName(d.getAssignedTo().fullName())
                        .phone(d.getAssignedTo().getPhone())
                        .build())
                .rejectReason(d.getRejectReason())
                .respondedAt(withZone(d.getRespondedAt()))
                .officerConfirmedAt(withZone(d.getIncident().getOfficerConfirmedAt()))
                .citizenConfirmedAt(withZone(d.getIncident().getCitizenConfirmedAt()))
                .station(Station.builder()
                        .id(s.getId()).name(s.getName()).agencyType(s.getAgencyType()).level(s.getLevel())
                        .address(s.getAddress()).phone(s.getPhone())
                        .latitude(s.getLatitude()).longitude(s.getLongitude()).build())
                .destination(destination)
                .route(route == null ? null : Route.builder()
                        .origin(Point.builder().latitude(route.getOriginLat()).longitude(route.getOriginLng())
                                .address(route.getOriginAddress()).build())
                        .distanceM(route.getDistanceM())
                        .durationS(route.getDurationS())
                        .durationTrafficS(route.getDurationTrafficS())
                        .polyline(route.getPolyline())
                        .provider(route.getProvider())
                        .computedAt(withZone(route.getComputedAt()))
                        .build())
                .dispatchedAt(withZone(d.getDispatchedAt()))
                .departedAt(withZone(d.getDepartedAt()))
                .arrivedAt(withZone(d.getArrivedAt()))
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
