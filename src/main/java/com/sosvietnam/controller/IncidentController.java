package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.request.OfficerCancelRequest;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
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
    private final com.sosvietnam.service.DispatchService dispatchService;

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
                    + "claim/act, held_by_me = caller holds it. days (1-90, optional) = only incidents reported in "
                    + "the last N days. Live updates: STOMP /user/queue/incidents. Role required: OFFICER, ADMIN")
    @GetMapping("/queue")
    public ResponseEntity<Object> getQueue(
            @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize,
            @RequestParam(value = "days", required = false) Integer days) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident queue",
                incidentService.getQueue(pageNo, pageSize, days));
    }

    @Operation(summary = "Get the incident history of my unit",
            description = "Closed incidents (RESOLVED / CANCELLED) the caller may see (same scope as the queue), newest "
                    + "first, paged (ListResponse). days 1-90 (default 7), q = search id / citizen / phone / address / "
                    + "ward (accents ignored), agency = POLICE | CSGT | FIRE (ADMIN only). Role required: OFFICER, ADMIN")
    @GetMapping("/history")
    public ResponseEntity<Object> getHistory(
            @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "20") int pageSize,
            @RequestParam(value = "days", required = false) Integer days,
            @RequestParam(value = "q", required = false) String q,
            @RequestParam(value = "agency", required = false) AgencyType agency) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident history",
                incidentService.getHistory(days, q, agency, pageNo, pageSize));
    }

    @Operation(summary = "Get the detail of one incident",
            description = "Incident info, media (url = GET /incidents/{id}/media/{file}), dispatched units and the claim "
                    + "history (who took / released / handed over). Same scope as the queue: 403 outside the caller's "
                    + "agency / area. Role required: OFFICER, ADMIN")
    @GetMapping("/{id}")
    public ResponseEntity<Object> getDetail(@PathVariable("id") String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident", incidentService.getDetail(id));
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

    @Operation(summary = "Officer: verification done, request dispatch of forces (VERIFYING -> RESOLVING)",
            description = "Optional body { field_member_id }: the field officer of my unit, normally picked later in "
                    + "step 2 of the dispatch window. Only the officer holding the incident. 409 if it is no longer VERIFYING. Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/request-dispatch")
    public ResponseEntity<Object> requestDispatch(@PathVariable("id") String id,
                                                  @RequestBody(required = false) @jakarta.validation.Valid
                                                  com.sosvietnam.model.payload.request.RequestDispatchRequest request) {
        java.util.UUID fieldMemberId = request == null ? null : request.getFieldMemberId();
        if (fieldMemberId != null) dispatchService.checkFieldMember(id, fieldMemberId);
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã gửi yêu cầu điều động lực lượng",
                incidentService.requestDispatch(id, fieldMemberId));
    }

    @Operation(summary = "Move a ward incident up to the province right now",
            description = "Officer holding it (JWT; VERIFYING or RESOLVING), or the citizen (header X-SOS-Access-Token) "
                    + "once the incident is VERIFYING past its 15-minute deadline. The incident goes back to PENDING at "
                    + "province level without a handler. Officer: body { reason } required (5-500 characters), saved as "
                    + "escalation_note. Role required: OFFICER, ADMIN or citizen token")
    @PostMapping("/{id}/escalate")
    public ResponseEntity<Object> escalateNow(
            @PathVariable("id") String id,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken,
            @Valid @RequestBody(required = false) com.sosvietnam.model.payload.request.EscalateRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã chuyển ca lên cấp tỉnh/thành phố",
                incidentService.escalateNow(id, citizenAccessToken, request == null ? null : request.getReason()));
    }

    @Operation(summary = "Citizen: look up a past incident by lookup code + last 4 phone digits",
            description = "body { lookup_code: 'K7M2-QX9P', phone_last4: '5678' }. 404 when code or phone does not match. "
                    + "Role required: none")
    @PostMapping("/lookup")
    public ResponseEntity<Object> lookupIncident(@Valid @RequestBody IncidentLookupRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved incident", incidentService.lookup(request));
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

    @Operation(summary = "Move a held incident to another force (wrong force chosen by the citizen)",
            description = "Officer holding it while VERIFYING. body { agency: POLICE | CSGT | FIRE, reason (5-500) }. "
                    + "The incident goes back to PENDING in the new force's queue (level routed again); the old force's "
                    + "queues drop the card. Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/transfer")
    public ResponseEntity<Object> transferAgency(@PathVariable("id") String id,
                                                 @Valid @RequestBody com.sosvietnam.model.payload.request.TransferIncidentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã chuyển ca sang lực lượng khác",
                incidentService.transferAgency(id, request));
    }

    @Operation(summary = "The officer holding the incident cancels it (any step), with a reason (5-255)",
            description = "Status -> CANCELLED (reason OFFICER_CANCELLED, text kept on the claim). Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/officer-cancel")
    public ResponseEntity<Object> cancelByOfficer(@PathVariable("id") String id,
                                                  @Valid @RequestBody OfficerCancelRequest request) {
        incidentService.cancelByOfficer(id, request.getReason());
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Đã huỷ ca");
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
