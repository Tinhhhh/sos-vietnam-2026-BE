package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.MediaKind;
import com.sosvietnam.model.payload.enums.UploaderType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Một file bằng chứng của ca (ảnh, video, ghi âm, ghi hình cuộc gọi). File nằm trên R2 (hoặc thư mục local)
 * theo storage_key; DB chỉ giữ thông tin về file. Xem file qua link ký ngắn hạn, không lưu URL.
 * 1 ca có nhiều file, 1 file thuộc đúng 1 ca.
 */
@Entity
@Table(name = "incident_media",
        uniqueConstraints = @UniqueConstraint(name = "ux_incident_media_file", columnNames = {"incident_id", "file_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaKind kind;

    // Tên file trong thư mục của ca: GET /incidents/{id}/media/{fileName}
    @Column(name = "file_name", nullable = false, length = 100)
    private String fileName;

    // Key trên R2 / đường dẫn local: incidents/{incidentId}/{fileName}
    @Column(name = "storage_key", nullable = false, length = 300)
    private String storageKey;

    @Column(name = "original_name")
    private String originalName;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    // Video / ghi âm (giây)
    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    // Ảnh / video (pixel)
    private Integer width;

    private Integer height;

    // SHA-256 nội dung file: chứng minh bằng chứng không bị sửa sau khi tải lên
    @Column(length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "uploaded_by", nullable = false, length = 20)
    private UploaderType uploadedBy;

    // Cán bộ tải lên; null khi người báo tin tải
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by_account_id")
    private Account uploadedByAccount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null) createdAt = LocalDateTime.now();
    }
}
