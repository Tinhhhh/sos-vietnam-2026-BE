package com.sosvietnam.controller;

import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stations")
@RequiredArgsConstructor
public class StationController {

    private final GeoService geoService;

    @GetMapping("/nearest")
    public ResponseEntity<Map<String, Object>> getNearestStations(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(required = false) AgencyType agency,
            @RequestParam(defaultValue = "15000") double radius,
            @RequestParam(defaultValue = "5") int limit) {

        List<RescueStation> stations = geoService.findNearestStations(lat, lng, agency, radius, limit);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "total", stations.size(),
                "stations", stations
        ));
    }
}
