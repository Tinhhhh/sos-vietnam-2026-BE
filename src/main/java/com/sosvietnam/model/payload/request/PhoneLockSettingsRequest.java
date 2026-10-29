package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** PUT /api/v1/phone-locks/settings. */
@Data
public class PhoneLockSettingsRequest {

    @JsonProperty("enabled")
    private Boolean enabled;
}
