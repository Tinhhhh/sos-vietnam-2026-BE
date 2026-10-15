package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/** Body của POST /api/v1/incidents/{id}/dispatch: trực ban phân công trạm xử lý sự cố. */
@Data
public class DispatchRequest {
    @JsonProperty("station_id")
    private Long stationId;
    @JsonProperty("dispatcher_name")
    private String dispatcherName;
    @JsonProperty("note")
    private String note;
}
