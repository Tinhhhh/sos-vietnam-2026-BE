package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.IncidentDocumentRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.SignDocumentRequest;
import com.sosvietnam.service.IncidentDocumentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/incidents")
@RequiredArgsConstructor
@Tag(name = "Incident document", description = "Văn bản báo cáo (phiếu tiếp nhận & ứng phó sự cố) của ca đã hoàn tất")
public class IncidentDocumentController {

    private final IncidentDocumentService documentService;

    @Operation(summary = "Get the report document of a resolved incident",
            description = "saved = false: never saved, fields are a draft filled from the incident. "
                    + "Same scope as the incident history. Role required: OFFICER, ADMIN")
    @GetMapping("/{id}/document")
    public ResponseEntity<Object> get(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Incident document", documentService.get(id));
    }

    @Operation(summary = "Create / update the document content",
            description = "409 once both sides signed. Changing the content after a signature clears the signatures. "
                    + "Role required: OFFICER, ADMIN")
    @PutMapping("/{id}/document")
    public ResponseEntity<Object> save(@PathVariable String id, @RequestBody @Valid IncidentDocumentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Document saved", documentService.save(id, request));
    }

    @Operation(summary = "Officer signs the \"Cán bộ / chỉ huy tiếp nhận\" box",
            description = "type DRAW (signature_data = PNG data URL) or TYPE (name only). Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/document/sign-officer")
    public ResponseEntity<Object> signOfficer(@PathVariable String id, @RequestBody @Valid SignDocumentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Signed", documentService.signOfficer(id, request));
    }

    @Operation(summary = "Officer signs the citizen box on the citizen's behalf",
            description = "reason (>= 5 characters) and authorized = true (the citizen allowed it) are required. "
                    + "Role required: OFFICER, ADMIN")
    @PostMapping("/{id}/document/sign-proxy")
    public ResponseEntity<Object> signProxy(@PathVariable String id, @RequestBody @Valid SignDocumentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Signed on behalf of the citizen", documentService.signProxy(id, request));
    }

    @Operation(summary = "Download the saved document as Word (.docx)", description = "Role required: OFFICER, ADMIN")
    @GetMapping("/{id}/document/docx")
    public ResponseEntity<byte[]> docx(@PathVariable String id) {
        byte[] file = documentService.docx(id);
        String name = "Phieu_TiepNhan_UPSC_" + id + ".docx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8).build().toString())
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(file);
    }

    @Operation(summary = "Citizen reads the document of their incident",
            description = "Header X-SOS-Access-Token. 404 while officers have not saved a document. Phone is masked.")
    @GetMapping("/{id}/document/citizen")
    public ResponseEntity<Object> citizenGet(@PathVariable String id,
                                             @RequestHeader(IncidentController.CITIZEN_TOKEN_HEADER) String citizenToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Incident document", documentService.citizenGet(id, citizenToken));
    }

    @Operation(summary = "Citizen signs the document of their incident", description = "Header X-SOS-Access-Token.")
    @PostMapping("/{id}/document/citizen-sign")
    public ResponseEntity<Object> citizenSign(@PathVariable String id,
                                              @RequestHeader(IncidentController.CITIZEN_TOKEN_HEADER) String citizenToken,
                                              @RequestBody @Valid SignDocumentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Signed", documentService.citizenSign(id, citizenToken, request));
    }

    @Operation(summary = "Citizen reads the document with the lookup code + last 4 phone digits (no login)")
    @PostMapping("/lookup/document")
    public ResponseEntity<Object> lookupGet(@RequestBody @Valid IncidentLookupRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Incident document", documentService.lookupGet(request));
    }

    @Operation(summary = "Citizen signs with the lookup code + last 4 phone digits (no login)")
    @PostMapping("/lookup/document/sign")
    public ResponseEntity<Object> lookupSign(@RequestBody @Valid SignDocumentRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Signed", documentService.lookupSign(request));
    }
}
