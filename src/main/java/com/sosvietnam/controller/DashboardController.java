package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/** Tab "Thống Kê" của cán bộ: số liệu sự cố theo phạm vi đơn vị. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/dashboard")
@Tag(name = "Dashboard", description = "Incident statistics for officers (scope from the account)")
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "Get incident statistics",
            description = "from / to: YYYY-MM-DD, Vietnam days (default: last 7 days, max 366). Scope comes from the "
                    + "account: ward officers see incidents of their ward, province officers their province, ADMIN the "
                    + "whole country and may pass province / agency. Role required: OFFICER, ADMIN")
    @GetMapping("/summary")
    public ResponseEntity<Object> getSummary(
            @RequestParam(value = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(value = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "agency", required = false) AgencyType agency) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved dashboard summary",
                dashboardService.getSummary(from, to, province, agency));
    }
}
