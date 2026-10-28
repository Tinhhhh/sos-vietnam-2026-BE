package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.PhoneLock;
import com.sosvietnam.model.payload.enums.PhoneLockReason;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Một lần khoá SĐT gửi SOS (ADMIN xem / gỡ). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhoneLockResponse {

    @JsonProperty("id")
    private Long id;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("reason")
    private PhoneLockReason reason;

    @JsonProperty("reason_label")
    private String reasonLabel;

    @JsonProperty("locked_at")
    private LocalDateTime lockedAt;

    /** null khi vĩnh viễn. */
    @JsonProperty("locked_until")
    private LocalDateTime lockedUntil;

    @JsonProperty("permanent")
    private boolean permanent;

    @JsonProperty("active")
    private boolean active;

    @JsonProperty("lifted_at")
    private LocalDateTime liftedAt;

    @JsonProperty("lift_note")
    private String liftNote;

    public static PhoneLockResponse from(PhoneLock l, LocalDateTime now) {
        return PhoneLockResponse.builder()
                .id(l.getId())
                .phone(l.getPhone())
                .reason(l.getReason())
                .reasonLabel(l.getReason().getLabel())
                .lockedAt(l.getLockedAt())
                .lockedUntil(l.getLockedUntil())
                .permanent(l.isPermanent())
                .active(l.activeAt(now))
                .liftedAt(l.getLiftedAt())
                .liftNote(l.getLiftNote())
                .build();
    }
}
