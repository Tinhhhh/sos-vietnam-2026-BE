package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.DemoPurgeScope;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Body của POST /api/v1/settings/demo/purge: { scope: OPEN | ALL }. */
@Data
public class DemoPurgeRequest {
    @NotNull(message = "Chọn phạm vi dọn: OPEN hoặc ALL")
    @JsonProperty("scope")
    private DemoPurgeScope scope;
}
