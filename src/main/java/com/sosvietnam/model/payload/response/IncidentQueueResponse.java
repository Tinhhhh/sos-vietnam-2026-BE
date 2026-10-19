package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Một ca trong hàng đợi của trực ban. can_handle / held_by_me tính theo người xem nên mỗi người nhận
 * bản riêng. Thời gian có kèm múi giờ để đồng hồ đếm ngược ở FE đúng dù server chạy UTC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentQueueResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("status")
    private IncidentStatus status;

    @JsonProperty("routed_agency")
    private AgencyType routedAgency;

    @JsonProperty("routed_level")
    private UnitLevel routedLevel;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("address")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("incident_tags")
    private List<String> incidentTags;

    @JsonProperty("description")
    private String description;

    @JsonProperty("citizen_name")
    private String citizenName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("media_count")
    private int mediaCount;

    /** Ảnh hiện trường (thẻ ca hiện biểu tượng ảnh + số lượng). */
    @JsonProperty("image_count")
    private int imageCount;

    /** Video hiện trường (biểu tượng máy quay). */
    @JsonProperty("video_count")
    private int videoCount;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("escalate_at")
    private OffsetDateTime escalateAt;

    @JsonProperty("escalated_at")
    private OffsetDateTime escalatedAt;

    // Người đang giữ ca; null = chưa ai nhận
    @JsonProperty("handler")
    private Handler handler;

    // Ca do chính người xem đang giữ
    @JsonProperty("held_by_me")
    private boolean heldByMe;

    // Người xem đúng cấp đang giữ ca (được nhận / thao tác); false = chỉ theo dõi
    @JsonProperty("can_handle")
    private boolean canHandle;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Handler {
        @JsonProperty("account_id")
        private UUID accountId;
        @JsonProperty("full_name")
        private String fullName;
    }

    /** Số file đính kèm của một ca: tổng và theo loại. */
    public record MediaCounts(int total, int images, int videos) {
        public static final MediaCounts NONE = new MediaCounts(0, 0, 0);
    }

    public static IncidentQueueResponse from(Incident i, Handler handler, boolean heldByMe, boolean canHandle,
                                             MediaCounts media) {
        return IncidentQueueResponse.builder()
                .id(i.getId())
                .emergencyType(i.getEmergencyType())
                .status(i.getStatus())
                .routedAgency(i.getRoutedAgency())
                .routedLevel(i.getRoutedLevel())
                .province(i.provinceName())
                .ward(i.wardName())
                .address(i.getAddress())
                .latitude(i.getLatitude())
                .longitude(i.getLongitude())
                .incidentTags(i.tags())
                .description(i.getDescription())
                .citizenName(i.getCitizenName())
                .phone(i.getPhone())
                .mediaCount(media.total())
                .imageCount(media.images())
                .videoCount(media.videos())
                .createdAt(withZone(i.getCreatedAt()))
                .escalateAt(withZone(i.getEscalateAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .handler(handler)
                .heldByMe(heldByMe)
                .canHandle(canHandle)
                .build();
    }

    // LocalDateTime trong DB là giờ của server (LocalDateTime.now())
    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
