package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API trạm cứu hộ: danh sách trạm (bản đồ chỉ huy) và tìm trạm gần nhất theo toạ độ, lực lượng. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stations")
@Tag(name = "Station", description = "Rescue stations (police, fire, traffic, hospital)")
public class StationController {

    private final GeoService geoService;

    @Operation(summary = "Get all stations",
            description = "Optional filters: province (exact name, e.g. Cần Thơ), agency: POLICE | MOBILE_POLICE | CSGT | FIRE | "
                    + "HOSPITAL | TRAFFIC_RESCUE | MILITARY, level: NATIONAL | PROVINCE | WARD | ENTERPRISE. Role required: none")
    @GetMapping
    public ResponseEntity<Object> getStations(
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "agency", required = false) AgencyType agency,
            @RequestParam(value = "level", required = false) StationLevel level) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved stations",
                geoService.getStations(province, agency, level));
    }

    @Operation(summary = "Find nearest stations",
            description = "agency: POLICE | MOBILE_POLICE | CSGT | FIRE | HOSPITAL | TRAFFIC_RESCUE | MILITARY (optional). radius in meters. Role required: none")
    @GetMapping("/nearest")
    public ResponseEntity<Object> getNearestStations(
            @RequestParam("lat") double lat,
            @RequestParam("lng") double lng,
            @RequestParam(value = "agency", required = false) AgencyType agency,
            @RequestParam(value = "radius", defaultValue = "15000") double radius,
            @RequestParam(value = "limit", defaultValue = "5") int limit) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved nearest stations",
                geoService.findNearestStations(lat, lng, agency, radius, limit));
    }
}
