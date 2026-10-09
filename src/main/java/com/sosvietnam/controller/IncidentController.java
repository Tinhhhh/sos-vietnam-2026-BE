package com.sosvietnam.controller;

import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import com.sosvietnam.service.IncidentService;
import com.sosvietnam.service.MediaAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/incidents")
@RequiredArgsConstructor
public class IncidentController {

    public static final String CITIZEN_TOKEN_HEADER = "X-SOS-Access-Token";

    private final IncidentService incidentService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> reportIncident(@Valid @RequestBody CreateIncidentRequest request) {
        CreateIncidentResponse created = incidentService.createIncident(request);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Tiếp nhận tin báo SOS thành công",
                "incident", created.getIncident(),
                "citizenAccessToken", created.getCitizenAccessToken()
        ));
    }

    @GetMapping("/recent")
    public ResponseEntity<Map<String, Object>> getRecentIncidents() {
        List<IncidentResponse> incidents = incidentService.getRecentIncidents();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "total", incidents.size(),
                "incidents", incidents
        ));
    }

    @PostMapping("/{id}/dispatch")
    public ResponseEntity<Map<String, Object>> dispatchIncident(@PathVariable String id, @RequestBody DispatchRequest request) {
        IncidentResponse incident = incidentService.dispatchIncident(id, request);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Đã phân công trạm tiếp nhận cứu nạn",
                "incident", incident
        ));
    }

    @PostMapping(value = "/{id}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadMedia(
            @PathVariable String id,
            @RequestHeader(value = CITIZEN_TOKEN_HEADER, required = false) String citizenAccessToken,
            @RequestPart("files") List<MultipartFile> files) {
        List<IncidentResponse.MediaResponse> media = incidentService.addMedia(id, citizenAccessToken, files);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "message", "Đã đính kèm " + media.size() + " ảnh/video hiện trường",
                "media", media
        ));
    }

    @GetMapping("/{id}/media/{fileName}")
    public ResponseEntity<Resource> getMedia(@PathVariable String id, @PathVariable String fileName) {
        MediaAccess media = incidentService.loadMedia(id, fileName);
        if (media.isRedirect()) {
            // R2: send the browser to a short-lived signed link; it downloads straight from the bucket.
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(media.redirectUrl())
                    .cacheControl(CacheControl.noStore())
                    .build();
        }
        Resource file = media.file();
        MediaType type = MediaTypeFactory.getMediaType(file).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
                .body(file);
    }
}
