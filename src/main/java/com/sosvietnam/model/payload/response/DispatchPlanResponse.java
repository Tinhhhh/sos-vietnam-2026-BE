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
 * và màn theo dõi của người báo tin. Người báo tin thấy người đi hiện trường (`tracker`: họ tên, số hiệu,
 * SĐT để gọi) và vị trí GPS của họ trong suốt lượt điều động (thiết kế Traccar 24/10/2026); không có email.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DispatchPlanResponse {

    @JsonProperty("dispatch_id")
    private Long dispatchId;

    /** Người nhận ca = cán bộ đang giữ ca (incidents.handler_id); SĐT chỉ có ở bản cho cán bộ. */
    @JsonProperty("receiver")
    private Officer receiver;

    /** Người phụ trách hiện trường (field_member_id), null khi chưa xuất phát. */
    @JsonProperty("tracker")
    private Officer tracker;

    /** Điểm GPS mới nhất của người đi, chỉ khi lượt đang EN_ROUTE và đã có điểm. */
    @JsonProperty("last_position")
    private LivePosition lastPosition;

    /** Chỉ cho cán bộ: lúc cấp link người phụ trách hiện trường gần nhất (null = chưa cấp / đã huỷ). */
    @JsonProperty("field_link_issued_at")
    private OffsetDateTime fieldLinkIssuedAt;

    /**
     * Khi đang trên đường: GPS mới nhất cách điểm đến bao xa, "Đã đến" được bấm khi within (<= threshold_m =
     * 30 m + sai số GPS tối đa 50 m). null = chưa có điểm GPS.
     */
    @JsonProperty("arrival_check")
    private ArrivalCheck arrivalCheck;

    /** Đã đến bằng cách nào (GPS / HOLDER_OVERRIDE / CITIZEN) + lý do khi người giữ ca xác nhận không theo GPS. */
    @JsonProperty("arrival_source")
    private com.sosvietnam.model.payload.enums.ArrivalSource arrivalSource;

    @JsonProperty("arrival_source_label")
    private String arrivalSourceLabel;

    @JsonProperty("arrival_note")
    private String arrivalNote;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ArrivalCheck {
        @JsonProperty("distance_m")
        private int distanceM;
        @JsonProperty("threshold_m")
        private int thresholdM;
        @JsonProperty("within")
        private boolean within;
    }

    /** Chỉ cho cán bộ (dân nhận null): nút "Mô phỏng di chuyển" của ca demo. */
    @JsonProperty("simulation")
    private Simulation simulation;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Simulation {
        /** Bấm được: lượt EN_ROUTE, người đi có số hiệu, công tắc ca demo bật (hoặc ca [DEMO]). */
        @JsonProperty("available")
        private boolean available;
        /** Đang chạy. */
        @JsonProperty("running")
        private boolean running;
    }

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

    /** Vị trí người báo tin (GPS lúc gửi SOS): khác điểm đến khi xe dừng ở đường lớn ngoài hẻm. */
    @JsonProperty("sos_point")
    private Point sosPoint;

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
        @JsonProperty("member_id")
        private java.util.UUID memberId;
        @JsonProperty("full_name")
        private String fullName;
        @JsonProperty("phone")
        private String phone;
        /** Số hiệu = mã thiết bị Traccar Client của người này. */
        @JsonProperty("badge_number")
        private String badgeNumber;
    }

    /** Vị trí mới nhất của người đi hiện trường (Traccar, chỉ giữ trong RAM của BE). */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LivePosition {
        @JsonProperty("latitude")
        private double latitude;
        @JsonProperty("longitude")
        private double longitude;
        @JsonProperty("speed_kmh")
        private Double speedKmh;
        @JsonProperty("heading")
        private Double heading;
        @JsonProperty("accuracy_m")
        private Double accuracyM;
        /** Lúc điện thoại lấy vị trí (null nếu Traccar không gửi). */
        @JsonProperty("fix_time")
        private OffsetDateTime fixTime;
        /** Lúc BE nhận điểm: FE tính "mất tín hiệu" theo mốc này. */
        @JsonProperty("received_at")
        private OffsetDateTime receivedAt;
        /** TRACCAR (app trên điện thoại, ưu tiên) | BROWSER (trang link người phụ trách) */
        @JsonProperty("source")
        private String source;
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
        /** STATION / CUSTOM; VEHICLE = tính lại từ vị trí xe vì đi lệch tuyến */
        @JsonProperty("origin_source")
        private RouteOriginSource originSource;
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
                .statusLabel(d.statusLabel())
                .arrivalSource(d.getArrivalSource())
                .arrivalSourceLabel(d.getArrivalSource() == null ? null : d.getArrivalSource().getLabel())
                .arrivalNote(d.getArrivalNote())
                .step(d.getStatus().getStep())
                .needsDelegation(d.getStatus() == DispatchStatus.PENDING_ACCEPT || d.getStatus() == DispatchStatus.ACCEPTED)
                .assignedTo(officer(d.getAssignedTo()))
                .tracker(officer(d.getFieldMember()))
                .rejectReason(d.getRejectReason())
                .respondedAt(withZone(d.getRespondedAt()))
                .officerConfirmedAt(withZone(d.getIncident().getOfficerConfirmedAt()))
                .citizenConfirmedAt(withZone(d.getIncident().getCitizenConfirmedAt()))
                .station(Station.builder()
                        .id(s.getId()).name(s.getName()).agencyType(s.getAgencyType()).level(s.getLevel())
                        .address(s.getAddress()).phone(s.getPhone())
                        .latitude(s.getLatitude()).longitude(s.getLongitude()).build())
                .destination(destination)
                .sosPoint(d.getIncident().getLatitude() == null ? null : Point.builder()
                        .latitude(d.getIncident().getLatitude()).longitude(d.getIncident().getLongitude()).build())
                .route(route == null ? null : Route.builder()
                        .origin(Point.builder().latitude(route.getOriginLat()).longitude(route.getOriginLng())
                                .address(route.getOriginAddress()).build())
                        .distanceM(route.getDistanceM())
                        .durationS(route.getDurationS())
                        .durationTrafficS(route.getDurationTrafficS())
                        .polyline(route.getPolyline())
                        .provider(route.getProvider())
                        .originSource(route.getOriginSource())
                        .computedAt(withZone(route.getComputedAt()))
                        .build())
                .dispatchedAt(withZone(d.getDispatchedAt()))
                .departedAt(withZone(d.getDepartedAt()))
                .arrivedAt(withZone(d.getArrivedAt()))
                .build();
    }

    private static Officer officer(com.sosvietnam.model.entity.UnitMember m) {
        return m == null ? null : Officer.builder()
                .memberId(m.getId())
                .fullName(m.displayName())
                .phone(m.getPhone())
                .badgeNumber(m.getBadgeNumber())
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
