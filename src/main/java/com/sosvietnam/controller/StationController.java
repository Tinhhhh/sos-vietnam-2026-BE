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

import static com.sosvietnam.util.AppConstants.*;

/** API trạm cứu hộ: danh sách trạm (bản đồ chỉ huy) và tìm trạm gần nhất theo toạ độ, lực lượng. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/stations")
@Tag(name = "Station", description = "Rescue stations (police, traffic police, fire, hospital)")
public class StationController {

    private final GeoService geoService;

    @Operation(summary = "Get all stations (paged)",
            description = "Officer token (optional): officer, phone and email are filled from the matching unit account "
                    + "(commander, duty officer phone, unit email); without it only the station's own data. "
                    + "pageNo from 0, pageSize 1-1000. province: part of the province name, case-insensitive "
                    + "(\"c\" matches Cần Thơ, Cà Mau...). agency: POLICE | CSGT | FIRE | HOSPITAL. "
                    + "level: NATIONAL | PROVINCE | WARD | ENTERPRISE. sortBy: id | name | province | ward | agencyType | level, "
                    + "sortDir: asc | desc. Role required: none")
    @GetMapping
    public ResponseEntity<Object> getStations(
            @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = DEFAULT_PAGE_SIZE) int pageSize,
            @RequestParam(value = "province", required = false) String province,
            @RequestParam(value = "agency", required = false) AgencyType agency,
            @RequestParam(value = "level", required = false) StationLevel level,
            @RequestParam(value = "sortBy", defaultValue = DEFAULT_SORT_BY) String sortBy,
            @RequestParam(value = "sortDir", defaultValue = DEFAULT_SORT_DIRECTION) String sortDir) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved stations",
                geoService.getStations(pageNo, pageSize, province, agency, level, sortBy, sortDir));
    }

    @Operation(summary = "Find nearest stations",
            description = "agency: POLICE | CSGT | FIRE | HOSPITAL (optional). radius in meters. Unit contacts only with "
                    + "an officer token, like GET /stations. Role required: none")
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
