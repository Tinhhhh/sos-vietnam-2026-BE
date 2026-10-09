package com.sosvietnam.model.payload.response;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentMediaItem;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** Incident as returned by the API and WebSocket (no geometry, no token hash). */
@Data
@Builder
public class IncidentResponse {
    private String id;
    private String citizenName;
    private String phone;
    private EmergencyType emergencyType;
    private IncidentStatus status;
    private String description;
    private List<String> incidentTags;
    private String address;
    private String ward;
    private String province;
    private Double latitude;
    private Double longitude;
    private Integer accuracy;
    private List<MediaResponse> media;
    private Long assignedStationId;
    private String assignedDispatcher;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @Data
    @Builder
    public static class MediaResponse {
        private String type;
        private String url;
        private String originalName;
        private String contentType;
        private long size;
    }

    public static MediaResponse toMediaResponse(String incidentId, IncidentMediaItem item) {
        return MediaResponse.builder()
                .type(item.getType())
                .url("/api/incidents/" + incidentId + "/media/" + item.getFileName())
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
