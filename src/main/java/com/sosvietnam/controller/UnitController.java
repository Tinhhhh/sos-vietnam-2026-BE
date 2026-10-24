package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.AssignDutyRequest;
import com.sosvietnam.model.payload.request.UnitMemberRequest;
import com.sosvietnam.service.UnitService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/unit")
@RequiredArgsConstructor
@Tag(name = "Unit", description = "Cán bộ của đơn vị và trực ban (Cài đặt của tài khoản đơn vị)")
public class UnitController {

    private final UnitService unitService;

    @Operation(summary = "List the officers of my unit", description = "Never returns PINs. Role required: OFFICER")
    @GetMapping("/members")
    public ResponseEntity<Object> getMembers() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Unit officers", unitService.getMembers());
    }

    @Operation(summary = "Add an officer", description = "Returns a one-time activation_code (72 h) the officer uses to set a PIN. "
            + "Role required: OFFICER (commander or duty officer)")
    @PostMapping("/members")
    public ResponseEntity<Object> createMember(@RequestBody @Valid UnitMemberRequest request) {
        return ResponseBuilder.returnData(HttpStatus.CREATED, "Đã thêm cán bộ", unitService.createMember(request));
    }

    @Operation(summary = "Edit an officer", description = "Role required: OFFICER (commander or duty officer)")
    @PutMapping("/members/{memberId}")
    public ResponseEntity<Object> updateMember(@PathVariable UUID memberId, @RequestBody @Valid UnitMemberRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã lưu cán bộ", unitService.updateMember(memberId, request));
    }

    @Operation(summary = "Remove an officer", description = "Soft delete; their open sessions end. Not yourself, not the duty officer, "
            + "not someone holding an incident (409). Role required: OFFICER (commander or duty officer)")
    @DeleteMapping("/members/{memberId}")
    public ResponseEntity<Object> deleteMember(@PathVariable UUID memberId) {
        unitService.deleteMember(memberId);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Đã xoá cán bộ khỏi đơn vị");
    }

    @Operation(summary = "Reissue an officer's PIN", description = "Clears the PIN, unlocks, returns a new one-time activation_code. "
            + "Role required: OFFICER (commander or duty officer)")
    @PostMapping("/members/{memberId}/reset-pin")
    public ResponseEntity<Object> resetPin(@PathVariable UUID memberId) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã cấp lại PIN", unitService.resetPin(memberId));
    }

    @Operation(summary = "Current duty officer and history", description = "Role required: OFFICER")
    @GetMapping("/duty")
    public ResponseEntity<Object> getDuty() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Unit duty", unitService.getDuty());
    }

    @Operation(summary = "Hand the duty to another officer", description = "Closes the current assignment now and opens one for "
            + "member_id. Role required: OFFICER (commander or the current duty officer)")
    @PostMapping("/duty")
    public ResponseEntity<Object> assignDuty(@RequestBody @Valid AssignDutyRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã giao trực ban", unitService.assignDuty(request.getMemberId()));
    }
}
