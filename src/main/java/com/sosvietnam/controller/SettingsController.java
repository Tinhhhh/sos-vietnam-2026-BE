package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.DemoSettingsRequest;
import com.sosvietnam.service.DemoModeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
@Tag(name = "Settings", description = "Cài đặt chung của hệ thống")
public class SettingsController {

    private final DemoModeService demoModeService;

    @Operation(summary = "Demo incidents switch",
            description = "generate = DemoIncidentSeeder creates [DEMO] incidents every minute; hidden = [DEMO] incidents "
                    + "are left out of the queue, history and officers' STOMP messages; open_count = open [DEMO] "
                    + "incidents. Shared by every account. Role required: OFFICER, ADMIN")
    @GetMapping("/demo")
    public ResponseEntity<Object> getDemo() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully retrieved demo settings", demoModeService.get());
    }

    @Operation(summary = "Change the demo incidents switch",
            description = "Body { generate?, hidden? }: a missing field is kept. Applies at once for everyone (no restart); "
                    + "open queues drop or get back the [DEMO] cards. Role required: OFFICER, ADMIN")
    @PutMapping("/demo")
    public ResponseEntity<Object> updateDemo(@RequestBody DemoSettingsRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully updated demo settings",
                demoModeService.update(request));
    }

    @Operation(summary = "Delete [DEMO] incidents",
            description = "Body { scope }: OPEN = open [DEMO] incidents (pending, verifying, dispatching), ALL = every "
                    + "[DEMO] incident. Deleted for good with their claims, trips, routes, reports, documents and media "
                    + "rows (R2 files stay); open queues drop the cards. Returns { scope, deleted, settings }. "
                    + "Role required: OFFICER, ADMIN")
    @PostMapping("/demo/purge")
    public ResponseEntity<Object> purgeDemo(@RequestBody @jakarta.validation.Valid com.sosvietnam.model.payload.request.DemoPurgeRequest request) {
        var result = demoModeService.purge(request.getScope());
        return ResponseBuilder.returnData(HttpStatus.OK, "Đã xoá " + result.getDeleted() + " ca [DEMO]", result);
    }
}
