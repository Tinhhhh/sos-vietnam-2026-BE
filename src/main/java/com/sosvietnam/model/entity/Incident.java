package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @Column(length = 50)
    private String id; // e.g. SOS-2026-XXXX

    @Column(name = "citizen_name", length = 150)
    private String citizenName;

    @Column(length = 10)
    @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must be 10 digits starting with 0.")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_type", nullable = false, length = 30)
    private EmergencyType emergencyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Quick-pick labels chosen in the citizen form, e.g. "🔥 Cháy nhà dân / Chung
    // cư / Nhà xưởng"
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "incident_tags", columnDefinition = "jsonb")
    private List<String> incidentTags = new ArrayList<>();

    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "media", columnDefinition = "jsonb")
    private List<IncidentMediaItem> media = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String address;

    @Column(length = 150)
    private String ward;

    @Column(length = 150)
    private String province;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // GPS accuracy reported by the citizen's device, in meters
    @Column(name = "accuracy")
    private Integer accuracy;

    // Jackson cannot serialize JTS geometry; API responses use IncidentResponse
    // instead.
    @JsonIgnore
    @Column(columnDefinition = "geometry(Point, 4326)")
    private Point location;

    // SHA-256 of the access token returned once to the citizen who reported the
    // incident
    @JsonIgnore
    @Column(name = "citizen_token_hash", length = 64)
    private String citizenTokenHash;

    @Column(name = "assigned_station_id")
    private Long assignedStationId;

    @Column(name = "assigned_dispatcher", length = 100)
    private String assignedDispatcher;

    @Column(name = "audio_url", columnDefinition = "TEXT")
    private String audioUrl;

    @Column(name = "transcript", columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (status == null)
            status = IncidentStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
