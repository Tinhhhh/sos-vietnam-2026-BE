package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.service.OfficerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/officers")
@RequiredArgsConstructor
@Tag(name = "Officers", description = "Trạng thái cán bộ của đơn vị")
public class OfficerController {

    private final OfficerService officerService;

    @Operation(summary = "Get the officers of my unit with their status",
            description = "Same unit as the caller (agency, level, province; ward for ward units), the caller included; "
                    + "ADMIN gets every active officer. online = has the command console open (STOMP connection), "
                    + "busy = holds an incident (held_incident). Role required: OFFICER, ADMIN")
    @GetMapping("/status")
    public ResponseEntity<Object> getUnitOfficers() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved officers", officerService.getUnitOfficers());
    }
}
