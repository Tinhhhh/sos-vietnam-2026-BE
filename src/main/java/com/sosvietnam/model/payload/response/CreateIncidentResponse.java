package com.sosvietnam.model.payload.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Result of a citizen SOS report. citizenAccessToken is returned only here;
 * the citizen sends it back in the X-SOS-Access-Token header to act on the
 * incident (upload media, later: track, chat, mark safe).
 */
@Data
@AllArgsConstructor
public class CreateIncidentResponse {
    private IncidentResponse incident;
    private String citizenAccessToken;
}
