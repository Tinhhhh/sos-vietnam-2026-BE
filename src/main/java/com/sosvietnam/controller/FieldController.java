package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.FieldPositionRequest;
import com.sosvietnam.service.DispatchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Trang của người phụ trách hiện trường (link /f/{token}, không đăng nhập). Mọi API dùng header X-SOS-Field-Token;
 * mã sai / đã cấp lại / đổi người -> 401, ca đã đóng -> 410.
 */
@RestController
@RequestMapping("/api/v1/field")
@RequiredArgsConstructor
@Tag(name = "Field", description = "Field officer link: case, route, browser GPS, arrival (no login)")
public class FieldController {

    public static final String FIELD_TOKEN_HEADER = "X-SOS-Field-Token";

    private final DispatchService dispatchService;

    @Operation(summary = "Case + dispatch plan for the field officer",
            description = "Header X-SOS-Field-Token. Includes the citizen's phone, the route and the last GPS point. "
                    + "401 bad / replaced token, 410 case closed. Public (token checked by the service)")
    @GetMapping("/plan")
    public ResponseEntity<Object> getPlan(@RequestHeader(value = FIELD_TOKEN_HEADER, required = false) String token) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Field plan", dispatchService.getFieldPlan(token));
    }

    @Operation(summary = "One GPS point from the field officer's browser",
            description = "Used only when the Traccar app has sent no point for 20 s (the app is more accurate). "
                    + "data = true when the point was taken. Public (field token)")
    @PostMapping("/position")
    public ResponseEntity<Object> position(@RequestHeader(value = FIELD_TOKEN_HEADER, required = false) String token,
                                           @RequestBody @Valid FieldPositionRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Position", dispatchService.fieldPosition(token, request));
    }

    @Operation(summary = "\"Đã đến\" pressed by the field officer (EN_ROUTE -> ARRIVED)",
            description = "Public (field token); 409 when the force is not on the way")
    @PostMapping("/arrive")
    public ResponseEntity<Object> arrive(@RequestHeader(value = FIELD_TOKEN_HEADER, required = false) String token) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Arrived", dispatchService.fieldArrive(token));
    }
}
