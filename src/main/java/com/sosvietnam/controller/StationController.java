package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.GeoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/** API trạm cứu hộ: tìm trạm gần nhất theo toạ độ và lực lượng. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stations")
@Tag(name = "Station", description = "Rescue stations (police, fire, traffic, hospital)")
public class StationController {

    private final GeoService geoService;

    @Operation(summary = "Find nearest stations",
            description = "agency: POLICE | FIRE | TRAFFIC_RESCUE | HOSPITAL | MILITARY (optional). radius in meters. Role required: none")
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
