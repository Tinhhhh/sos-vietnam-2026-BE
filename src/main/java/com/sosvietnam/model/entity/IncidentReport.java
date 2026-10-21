package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.ReportResult;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/** Văn bản kết thúc một ca (bước 4 điều động, hoặc cán bộ kết thúc bắt buộc). */
@Entity
@Table(name = "incident_reports")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentReport {

    @Id
    @Column(name = "incident_id", length = 50)
    private String incidentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", length = 20, nullable = false)
    private ReportResult result;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "forces", length = 500)
    private String forces;

    @Column(name = "note", length = 1000)
    private String note;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
