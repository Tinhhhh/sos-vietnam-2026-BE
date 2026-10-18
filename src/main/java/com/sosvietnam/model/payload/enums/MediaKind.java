package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Loại file bằng chứng của một ca (bảng incident_media). */
@Getter
public enum MediaKind {
    IMAGE("Ảnh hiện trường"),
    VIDEO("Video hiện trường"),
    AUDIO("File ghi âm"),
    CALL_RECORDING("Ghi hình / ghi âm cuộc gọi");

    private final String label;

    MediaKind(String label) {
        this.label = label;
    }
}
