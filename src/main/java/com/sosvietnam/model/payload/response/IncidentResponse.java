package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentMedia;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Sự cố trả về qua API và WebSocket (không kèm geometry, không kèm hash token). JSON snake_case. */
@Data
@Builder
public class IncidentResponse {
    @JsonProperty("id")
    private String id;
    @JsonProperty("citizen_name")
    private String citizenName;
    @JsonProperty("phone")
    private String phone;
    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;
    @JsonProperty("status")
    private IncidentStatus status;
    /** Mã ca (XXXX-XXXX): dân dùng để Tra cứu vào lại ca / xem kết quả (hiện ngay trên màn chờ) */
    @JsonProperty("lookup_code")
    private String lookupCode;
    @JsonProperty("description")
    private String description;
    @JsonProperty("incident_tags")
    private List<String> incidentTags;
    @JsonProperty("address")
    private String address;
    @JsonProperty("ward")
    private String ward;
    @JsonProperty("ward_id")
    private Long wardId;
    @JsonProperty("province")
    private String province;
    @JsonProperty("province_id")
    private String provinceId;
    @JsonProperty("latitude")
    private Double latitude;
    @JsonProperty("longitude")
    private Double longitude;
    @JsonProperty("accuracy")
    private Integer accuracy;
    @JsonProperty("media")
    private List<MediaResponse> media;
    /** Trạm / lực lượng đã được điều động tới ca (bảng incident_dispatches). */
    @JsonProperty("dispatches")
    private List<DispatchResponse> dispatches;
    @JsonProperty("created_at")
    private LocalDateTime createdAt;
    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class MediaResponse {
        @JsonProperty("type")
        private String type;
        @JsonProperty("url")
        private String url;
        @JsonProperty("original_name")
        private String originalName;
        @JsonProperty("content_type")
        private String contentType;
        @JsonProperty("size")
        private long size;
    }

    @Data
    @Builder
    public static class DispatchResponse {
        @JsonProperty("station_id")
        private Long stationId;
        @JsonProperty("station_name")
        private String stationName;
        @JsonProperty("unit_level")
        private String unitLevel;
        @JsonProperty("note")
        private String note;
        @JsonProperty("dispatched_at")
        private LocalDateTime dispatchedAt;
    }

    public static MediaResponse toMediaResponse(String incidentId, IncidentMedia item) {
        return MediaResponse.builder()
                .type(item.getKind().name())
                .url("/api/v1/incidents/" + incidentId + "/media/" + item.getFileName())
                .originalName(item.getOriginalName())
                .contentType(item.getContentType())
                .size(item.getSizeBytes() == null ? 0 : item.getSizeBytes())
                .build();
    }

    public static DispatchResponse toDispatchResponse(IncidentDispatch d) {
        return DispatchResponse.builder()
                .stationId(d.getStation().getId())
                .stationName(d.getStation().getName())
                .unitLevel(d.getUnitLevel() == null ? null : d.getUnitLevel().name())
                .note(d.getNote())
                .dispatchedAt(d.getDispatchedAt())
                .build();
    }

    public static IncidentResponse from(Incident incident, List<IncidentMedia> mediaItems, List<IncidentDispatch> dispatchItems) {
        List<MediaResponse> media = mediaItems.stream().map(m -> toMediaResponse(incident.getId(), m)).toList();

        return IncidentResponse.builder()
                .id(incident.getId())
                .citizenName(incident.getCitizenName())
                .phone(incident.getPhone())
                .emergencyType(incident.getEmergencyType())
                .status(incident.getStatus())
                .lookupCode(com.sosvietnam.util.LookupCodes.format(incident.getLookupCode()))
                .description(incident.getDescription())
                .incidentTags(incident.tags())
                .address(incident.getAddress())
                .ward(incident.wardName())
                .wardId(incident.wardId())
                .province(incident.provinceName())
                .provinceId(incident.provinceId())
                .latitude(incident.getLatitude())
                .longitude(incident.getLongitude())
                .accuracy(incident.getAccuracy())
                .media(media)
                .dispatches(dispatchItems.stream().map(IncidentResponse::toDispatchResponse).toList())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }
}
