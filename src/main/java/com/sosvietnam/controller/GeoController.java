package com.sosvietnam.controller;

import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.service.BandoSyncService;
import com.sosvietnam.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoService geoService;
    private final BandoSyncService bandoSyncService;

    @GetMapping("/provinces")
    public ResponseEntity<Map<String, Object>> getProvinces() {
        List<Province> provinces = geoService.getAllProvinces();
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalProvinces", provinces.size(),
                "provinces", provinces
        ));
    }

    @GetMapping("/wards")
    public ResponseEntity<Map<String, Object>> getWards(@RequestParam String provinceCode) {
        List<Ward> wards = geoService.getWardsByProvince(provinceCode);
        return ResponseEntity.ok(Map.of(
                "ok", true,
                "totalWards", wards.size(),
                "wards", wards
        ));
    }

    @GetMapping("/resolve")
    public ResponseEntity<Map<String, Object>> resolveLocation(@RequestParam double lat, @RequestParam double lng) {
        Map<String, Object> location = geoService.resolveLocation(lat, lng);
        return ResponseEntity.ok(Map.of("ok", true, "data", location));
    }

    @GetMapping("/sync-status")
    public ResponseEntity<Map<String, Object>> getSyncStatus() {
        return ResponseEntity.ok(Map.of("ok", true, "sync", bandoSyncService.getStatus()));
    }

    @PostMapping("/sync-now")
    public ResponseEntity<Map<String, Object>> triggerSyncNow() {
        return ResponseEntity.ok(bandoSyncService.syncFromNationalDatabase());
    }
}
