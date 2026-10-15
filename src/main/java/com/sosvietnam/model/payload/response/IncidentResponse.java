package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentMediaItem;
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
    @JsonProperty("description")
    private String description;
    @JsonProperty("incident_tags")
    private List<String> incidentTags;
    @JsonProperty("address")
    private String address;
    @JsonProperty("ward")
    private String ward;
    @JsonProperty("province")
    private String province;
    @JsonProperty("latitude")
    private Double latitude;
    @JsonProperty("longitude")
    private Double longitude;
    @JsonProperty("accuracy")
    private Integer accuracy;
    @JsonProperty("media")
    private List<MediaResponse> media;
    @JsonProperty("assigned_station_id")
    private Long assignedStationId;
    @JsonProperty("assigned_dispatcher")
    private String assignedDispatcher;
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

    public static MediaResponse toMediaResponse(String incidentId, IncidentMediaItem item) {
        return MediaResponse.builder()
                .type(item.getType())
                .url("/api/v1/incidents/" + incidentId + "/media/" + item.getFileName())
                .originalName(item.getOriginalName())
                .contentType(item.getContentType())
                .size(item.getSize())
                .build();
    }

    public static IncidentResponse from(Incident incident) {
        List<MediaResponse> media = incident.getMedia() == null ? List.of()
                : incident.getMedia().stream().map(m -> toMediaResponse(incident.getId(), m)).toList();

        return IncidentResponse.builder()
                .id(incident.getId())
                .citizenName(incident.getCitizenName())
                .phone(incident.getPhone())
                .emergencyType(incident.getEmergencyType())
                .status(incident.getStatus())
                .description(incident.getDescription())
                .incidentTags(incident.getIncidentTags() == null ? List.of() : incident.getIncidentTags())
                .address(incident.getAddress())
                .ward(incident.getWard())
                .province(incident.getProvince())
                .latitude(incident.getLatitude())
                .longitude(incident.getLongitude())
                .accuracy(incident.getAccuracy())
                .media(media)
                .assignedStationId(incident.getAssignedStationId())
                .assignedDispatcher(incident.getAssignedDispatcher())
                .createdAt(incident.getCreatedAt())
                .updatedAt(incident.getUpdatedAt())
                .build();
    }
}
