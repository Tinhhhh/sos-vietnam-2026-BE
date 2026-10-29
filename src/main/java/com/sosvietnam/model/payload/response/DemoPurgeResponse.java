package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.DemoPurgeScope;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Kết quả "Dọn ca [DEMO]": đã xoá bao nhiêu ca, và trạng thái ca demo sau khi dọn. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DemoPurgeResponse {
    @JsonProperty("scope")
    private DemoPurgeScope scope;

    @JsonProperty("deleted")
    private long deleted;

    @JsonProperty("settings")
    private DemoSettingsResponse settings;
}
