package com.sosvietnam.controller;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.OfficerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.sosvietnam.util.AppConstants.DEFAULT_PAGE_NUMBER;

@RestController
@RequestMapping("/api/v1/officers")
@RequiredArgsConstructor
@Tag(name = "Officers", description = "Trạng thái cán bộ của đơn vị")
public class OfficerController {

    private final OfficerService officerService;

    @Operation(summary = "Get one page of the officers of a unit with their status",
            description = "OFFICER: the caller's unit (agency, level, province; ward for ward units), the caller included; a "
                    + "province officer may pick level=WARD + ward_id of a ward in their province (same agency). Other "
                    + "unit filters are ignored. ADMIN: pick one unit, level=PROVINCE|WARD + province_id "
                    + "(+ ward_id for WARD, must be in the province), agency optional; missing / wrong -> 400. "
                    + "online=true: only officers with the command console open. online = STOMP connection, "
                    + "busy = holds an incident (held_incident). pageSize max 100. Role required: OFFICER, ADMIN")
    @GetMapping("/status")
    public ResponseEntity<Object> getUnitOfficers(
            @RequestParam(value = "level", required = false) UnitLevel level,
            @RequestParam(value = "province_id", required = false) String provinceId,
            @RequestParam(value = "ward_id", required = false) Long wardId,
            @RequestParam(value = "agency", required = false) AgencyType agency,
            @RequestParam(value = "online", required = false) Boolean online,
            @RequestParam(value = "pageNo", defaultValue = DEFAULT_PAGE_NUMBER) int pageNo,
            @RequestParam(value = "pageSize", defaultValue = "50") int pageSize) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved officers",
                officerService.getUnitOfficers(level, provinceId, wardId, agency, online, pageNo, pageSize));
    }
}
