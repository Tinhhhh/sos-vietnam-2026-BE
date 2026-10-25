package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.NewAccountRequest;
import com.sosvietnam.service.AccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/v1/admin/accounts")
@RequiredArgsConstructor
@Tag(name = "Admin - Accounts", description = "Quản lý tài khoản (chỉ ADMIN)")
public class AdminAccountController {

    private static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AccountService accountService;

    @Operation(summary = "Create new officer / admin account",
            description = "address = detail only (house number, street); ward/province come from ward_id/province_id. Role required: ADMIN")
    @PostMapping
    public ResponseEntity<Object> createNewAccount(@Valid @RequestBody NewAccountRequest request) {
        return ResponseBuilder.returnData(HttpStatus.CREATED, "Tạo tài khoản thành công",
                accountService.createAccountByAdmin(request));
    }

    @Operation(summary = "Export accounts + unit officers to Excel (.xlsx)",
            description = "Optional filters province_id, agency, level. Sheet 1 = accounts (commander, duty officer), sheet 2 = "
                    + "officers of the units. Password / PIN columns show only seed values still in use (DB keeps bcrypt "
                    + "hashes only). Role required: ADMIN")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportAccounts(
            @RequestParam(name = "province_id", required = false) String provinceId,
            @RequestParam(name = "agency", required = false) AgencyType agency,
            @RequestParam(name = "level", required = false) UnitLevel level) throws IOException {
        String fileName = "danh-sach-tai-khoan-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm")) + ".xlsx";
        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8).build().toString())
                .body(accountService.exportAccountsToExcel(provinceId, agency, level));
    }
}
