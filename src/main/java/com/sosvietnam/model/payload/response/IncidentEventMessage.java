package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Tin STOMP gửi tới /user/queue/incidents của từng trực ban có quyền xem ca. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IncidentEventMessage {
    @JsonProperty("event")
    private IncidentEvent event;

    @JsonProperty("incident")
    private IncidentQueueResponse incident;
}
