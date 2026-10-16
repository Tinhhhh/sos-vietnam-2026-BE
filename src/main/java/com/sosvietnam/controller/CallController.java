package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.service.CallService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

import static com.sosvietnam.controller.IncidentController.CITIZEN_TOKEN_HEADER;

/**
 * Video call giữa người báo tin và cán bộ giữ ca.
 * REST: máy chủ ICE. STOMP: SEND /app/calls (tín hiệu WebRTC), nhận ở /user/queue/calls.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/calls")
@Tag(name = "Calls", description = "WebRTC video call between the citizen and the officer holding the incident")
public class CallController {

    private final CallService callService;

    @Operation(summary = "ICE servers (STUN + short-lived Cloudflare TURN) for a call",
            description = "Officer: JWT. Citizen: header X-SOS-Access-Token + ?incident_id=. "
                    + "relay=false means STUN only (TURN not configured / unavailable). Role required: OFFICER, ADMIN or citizen token")
    @GetMapping("/ice-servers")
    public ResponseEntity<Object> getIceServers(
            @RequestParam(value = "incident_id", required = false) String incidentId,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved ICE servers",
                callService.getIceServers(incidentId, citizenAccessToken));
    }

    /** STOMP SEND /app/calls: chuyển tiếp tín hiệu tới bên kia của ca (không có phản hồi trực tiếp). */
    @MessageMapping("/calls")
    public void relaySignal(@Payload CallSignalMessage signal, Principal principal) {
        callService.relay(principal, signal);
    }
}
