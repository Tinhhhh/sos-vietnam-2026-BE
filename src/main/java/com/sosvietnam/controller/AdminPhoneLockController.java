package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.LiftPhoneLockRequest;
import com.sosvietnam.service.PhoneLockService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/phone-locks")
@RequiredArgsConstructor
@Tag(name = "Admin - Phone locks", description = "SĐT bị khoá gửi SOS vì báo tin giả (chỉ ADMIN)")
public class AdminPhoneLockController {

    private final PhoneLockService phoneLockService;

    @Operation(summary = "List phone locks",
            description = "phone: exact number (optional); active=true (default) = only locks in force. A lock is 24 h; "
                    + "the 3rd lock within 30 days is permanent (locked_until null). Role required: ADMIN")
    @GetMapping
    public ResponseEntity<Object> list(@RequestParam(required = false) String phone,
                                       @RequestParam(defaultValue = "true") boolean active,
                                       @RequestParam(defaultValue = "0") int pageNo,
                                       @RequestParam(defaultValue = "50") int pageSize) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved phone locks",
                phoneLockService.list(phone, active, pageNo, pageSize));
    }

    @Operation(summary = "Lift a phone lock",
            description = "Body { note } (>= 5 characters): why it is lifted (the person explained it to the police / "
                    + "contacted the system). Lifted locks no longer count towards the permanent lock. Role required: ADMIN")
    @PostMapping("/{id}/lift")
    public ResponseEntity<Object> lift(@PathVariable Long id, @RequestBody LiftPhoneLockRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã gỡ khoá", phoneLockService.lift(id, request.getNote()));
    }
}
