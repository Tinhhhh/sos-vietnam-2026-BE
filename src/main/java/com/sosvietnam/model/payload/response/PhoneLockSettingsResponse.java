package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Công tắc khoá SĐT báo tin giả (ADMIN). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneLockSettingsResponse {

    /** false = không khoá SĐT mới, các khoá đang có không chặn gửi SOS (vẫn giữ để bật lại). */
    @JsonProperty("enabled")
    private boolean enabled;

    @JsonProperty("updated_at")
    private LocalDateTime updatedAt;
}
