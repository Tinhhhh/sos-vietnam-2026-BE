package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body POST /api/v1/admin/phone-locks/{id}/lift. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LiftPhoneLockRequest {
    @Size(max = 255)
    @JsonProperty("note")
    private String note;
}
