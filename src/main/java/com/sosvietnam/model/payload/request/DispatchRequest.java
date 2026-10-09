package com.sosvietnam.model.payload.request;

import lombok.Data;

@Data
public class DispatchRequest {
    private Long stationId;
    private String dispatcherName;
    private String note;
}
