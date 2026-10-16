package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.request.ReleaseIncidentRequest;
import com.sosvietnam.model.payload.response.IncidentResponse;
import com.sosvietnam.service.IncidentService;
import com.sosvietnam.service.MediaAccess;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import static com.sosvietnam.util.AppConstants.DEFAULT_PAGE_NUMBER;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;

/**
 * API tin báo SOS.
 * - Người dân (không cần đăng nhập): tạo sự cố, đính kèm ảnh/video bằng citizen access token.
 * - Trực ban (OFFICER/ADMIN): xem sự cố gần đây, phân công trạm.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/incidents")
@Tag(name = "Incident", description = "Citizen SOS reports and dispatching")
public class IncidentController {

    public static final String CITIZEN_TOKEN_HEADER = "X-SOS-Access-Token";

    private final IncidentService incidentService;

    @Operation(summary = "Report a new SOS incident",
            description = "Returns the incident and a citizen_access_token (shown only once). Role required: none")
    @PostMapping
    public ResponseEntity<Object> reportIncident(@Valid @RequestBody CreateIncidentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.CREATED, "Tiếp nhận tin báo SOS thành công",
                incidentService.createIncident(request));
    }

    @Operation(summary = "Get the incident queue of my unit",
            description = "Active incidents (not resolved/cancelled) of the caller's agency and area, newest first, "
                    + "paged (ListResponse; pageNo from 0, pageSize 1-100, default 50). can_handle = caller may "
                    + "claim/act, held_by_me = caller holds it. Live updates: STOMP /user/queue/incidents. "
                    + "Role required: OFFICER, ADMIN")
    @GetMapping("/queue")
    public ResponseEntity<Object> getQueue(
            @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident queue",
                incidentService.getQueue(pageNo, pageSize));
    }

    @Operation(summary = "Claim (accept) an incident",
            description = "Locks the incident for the caller. 409 when someone else got it first or the caller already "
                    + "holds another incident (one incident per officer). Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/claim")
    public ResponseEntity<Object> claimIncident(@PathVariable("id") String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã tiếp nhận ca " + id,
                incidentService.claimIncident(id));
    }

    @Operation(summary = "Release or hand over an incident I hold",
            description = "No to_account_id: back to the shared queue (the 15-minute escalation clock keeps running "
                    + "from creation). With to_account_id: hand over to an idle officer of the same unit. "
                    + "Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/release")
    public ResponseEntity<Object> releaseIncident(@PathVariable("id") String id,
                                                  @Valid @RequestBody(required = false) ReleaseIncidentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã nhả ca " + id,
                incidentService.releaseIncident(id, request));
    }

    @Operation(summary = "Officers of my unit who can take over an incident I hold",
            description = "online = connected to the console, busy = already holds an incident. Role required: OFFICER, ADMIN")
    @GetMapping("/{id}/handover-candidates")
    public ResponseEntity<Object> getHandoverCandidates(@PathVariable("id") String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved handover candidates",
                incidentService.getHandoverCandidates(id));
    }

    @Operation(summary = "Assign a station to an incident", description = "Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/dispatch")
    public ResponseEntity<Object> dispatchIncident(@PathVariable("id") String id, @RequestBody DispatchRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã phân công trạm tiếp nhận cứu nạn",
                incidentService.dispatchIncident(id, request));
    }

    @Operation(summary = "Attach scene photos/videos to an incident",
            description = "multipart field 'files', header X-SOS-Access-Token. Role required: none (citizen token)")
    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Object> uploadMedia(
            @PathVariable("id") String id,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken,
            @RequestPart("files") List<MultipartFile> files) {
        List<IncidentResponse.MediaResponse> media = incidentService.addMedia(id, citizenAccessToken, files);
        return ResponseBuilder.returnData(HttpStatus.OK,
                "Đã đính kèm " + media.size() + " ảnh/video hiện trường", media);
    }

    @Operation(summary = "Citizen: status of their own incident (waiting screen)",
            description = "header X-SOS-Access-Token. Role required: none (citizen token)")
    @GetMapping("/{id}/tracking")
    public ResponseEntity<Object> getTracking(
            @PathVariable("id") String id,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident status",
                incidentService.getTracking(id, citizenAccessToken));
    }

    @Operation(summary = "Citizen: cancel their own SOS report",
            description = "header X-SOS-Access-Token, body optional { reason: MISTAKE | RESOLVED_SELF | CALLED_HOTLINE | OTHER }. "
                    + "Allowed while the incident is open (even if an officer holds it); 409 if already closed. "
                    + "Role required: none (citizen token)")
    @PostMapping("/{id}/cancel")
    public ResponseEntity<Object> cancelIncident(
            @PathVariable("id") String id,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken,
            @Valid @RequestBody(required = false) CancelIncidentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã huỷ tin báo SOS",
                incidentService.cancelByCitizen(id, citizenAccessToken, request));
    }

    @Operation(summary = "Download an attached photo/video",
            description = "Streams the file (local storage) or redirects to a signed link (R2). Role required: none")
    @GetMapping("/{id}/media/{fileName}")
    public ResponseEntity<Resource> getMedia(@PathVariable("id") String id, @PathVariable("fileName") String fileName) {
        MediaAccess media = incidentService.loadMedia(id, fileName);
        if (media.isRedirect()) {
            // R2: chuyển browser sang link có chữ ký (hết hạn ngắn), tải thẳng từ bucket
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(media.redirectUrl())
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        // Local: stream file trực tiếp (endpoint trả file nên không bọc ResponseBuilder)
        Resource file = media.file();
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file);
    }
}
