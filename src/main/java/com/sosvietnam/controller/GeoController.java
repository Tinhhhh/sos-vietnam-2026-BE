package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.BandoSyncService;
import com.sosvietnam.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * API địa giới hành chính: danh sách tỉnh, xã/phường, tra địa bàn theo toạ độ,
 * và đồng bộ ranh giới từ CSDL quốc gia (bando).
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/geo")
@Tag(name = "Geo", description = "Administrative boundaries: provinces, wards, reverse lookup")
public class GeoController {

    private final GeoService geoService;
    private final BandoSyncService bandoSyncService;

    @Operation(summary = "Get all provinces", description = "List of 34 provinces, sorted by name. Role required: none")
    @GetMapping("/provinces")
    public ResponseEntity<Object> getProvinces() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved provinces",
                geoService.getAllProvinces());
    }

    @Operation(summary = "Get wards of a province", description = "provinceCode: province code from /provinces. Role required: none")
    @GetMapping("/wards")
    public ResponseEntity<Object> getWards(@RequestParam("provinceCode") String provinceCode) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved wards",
                geoService.getWardsByProvince(provinceCode));
    }

    @Operation(summary = "Resolve ward and province from lat/lng",
            description = "province_code is null when the point is outside known boundaries. Role required: none")
    @GetMapping("/resolve")
    public ResponseEntity<Object> resolveLocation(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully resolved location",
                geoService.resolveLocation(lat, lng));
    }

    @Operation(summary = "Get boundary sync status", description = "Role required: ADMIN")
    @GetMapping("/sync-status")
    public ResponseEntity<Object> getSyncStatus() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved sync status",
                bandoSyncService.getStatus());
    }

    @Operation(summary = "Sync boundaries from the national database now", description = "Role required: ADMIN")
    @PostMapping("/sync-now")
    public ResponseEntity<Object> triggerSyncNow() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Boundary sync finished",
                bandoSyncService.syncFromNationalDatabase());
    }
}
