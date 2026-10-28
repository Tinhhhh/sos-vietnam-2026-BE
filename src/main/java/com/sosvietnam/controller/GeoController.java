package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.BandoSyncService;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.RoadSnapService;
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
    private final RoadSnapService roadSnapService;
    private final BandoSyncService bandoSyncService;

    @Operation(summary = "Get all provinces", description = "List of 34 provinces, sorted by name. Role required: none")
    @GetMapping("/provinces")
    public ResponseEntity<Object> getProvinces() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved provinces",
                geoService.getAllProvinces());
    }

    @Operation(summary = "Get wards of a province",
            description = "provinceId = provinces.code from /provinces. Sorted by name, 404 for an unknown province. Role required: none")
    @GetMapping("/provinces/{provinceId}/wards")
    public ResponseEntity<Object> getWards(@PathVariable("provinceId") String provinceId) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved wards",
                geoService.getWardsByProvince(provinceId));
    }

    @Operation(summary = "Resolve ward and province from lat/lng",
            description = "province_id is null when the point is outside known boundaries. Role required: none")
    @GetMapping("/resolve")
    public ResponseEntity<Object> resolveLocation(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully resolved location",
                geoService.resolveLocation(lat, lng));
    }

    @Operation(summary = "Where the vehicle should stop for an SOS point",
            description = "OSRM nearest (OpenStreetMap): nearest point on a named road that is not an alley (hẻm / kiệt / "
                    + "ngõ / ngách) within 300 m (source MAIN_ROAD), else the nearest drivable point (NEAREST_ROAD), else the "
                    + "point itself (NONE). For citizens in an alley the officer can use it as the dispatch destination. "
                    + "Role required: OFFICER, ADMIN")
    @GetMapping("/road-snap")
    public ResponseEntity<Object> roadSnap(@RequestParam("lat") double lat, @RequestParam("lng") double lng) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully snapped to the nearest road",
                roadSnapService.snap(lat, lng));
    }

    @Operation(summary = "Get map data versions",
            description = "Version, last update and last check of boundaries and rescue stations. Role required: OFFICER, ADMIN")
    @GetMapping("/data-status")
    public ResponseEntity<Object> getDataStatus() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved data status",
                geoService.getDataStatus());
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

    @Operation(summary = "Download original province / ward boundaries from bando in the background",
            description = "Takes about 20 minutes; progress in GET /geo/sync-status (boundary). Role required: ADMIN")
    @PostMapping("/sync-boundaries")
    public ResponseEntity<Object> triggerBoundarySync() {
        return ResponseBuilder.returnData(HttpStatus.ACCEPTED, "Boundary download started",
                bandoSyncService.startBoundarySync());
    }
}
