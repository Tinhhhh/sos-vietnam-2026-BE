package com.sosvietnam.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * One photo/video attached to an incident. Stored as an element of the
 * incidents.media jsonb column; the file itself lives in the upload folder.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentMediaItem {
    private String type;          // IMAGE | VIDEO
    private String fileName;      // generated name on disk
    private String originalName;
    private String contentType;
    private long size;
    private LocalDateTime uploadedAt;
}
