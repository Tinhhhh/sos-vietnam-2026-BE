package com.sosvietnam.controller;

import com.sosvietnam.model.payload.request.DelegateRequest;
import com.sosvietnam.model.payload.request.DispatchPlanRequest;
import com.sosvietnam.model.payload.request.FieldMemberRequest;
import com.sosvietnam.model.payload.request.FinishIncidentRequest;
import com.sosvietnam.model.payload.request.RejectDelegationRequest;
import com.sosvietnam.service.DispatchService;
import com.sosvietnam.model.payload.exception.ResponseBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/incidents/{id}/dispatch-plan")
@RequiredArgsConstructor
@Tag(name = "Dispatch", description = "Điều động lực lượng: trạm xuất phát, điểm đến, tuyến đường")
public class DispatchController {

    private final DispatchService dispatchService;

    @Operation(summary = "Get the current dispatch plan of an incident",
            description = "Officer who can view the incident (JWT), or the citizen (header X-SOS-Access-Token). "
                    + "data = null while no starting station is chosen. Role required: OFFICER, ADMIN or citizen token")
    @GetMapping
    public ResponseEntity<Object> getPlan(@PathVariable String id,
                                          @RequestHeader(value = IncidentController.CITIZEN_TOKEN_HEADER, required = false) String citizenToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Dispatch plan", dispatchService.getPlan(id, citizenToken));
    }

    @Operation(summary = "Choose / change the starting station, destination and route (step 1)",
            description = "Only the officer holding the incident while it is RESOLVING. delegate=true: the station belongs "
                    + "to another (ward) unit -> step 2 Uỷ quyền; false -> step 3 Theo dõi, field_member_id required "
                    + "(field officer of my unit, see /field-candidates). Role required: OFFICER, ADMIN")
    @PutMapping
    public ResponseEntity<Object> savePlan(@PathVariable String id, @RequestBody @Valid DispatchPlanRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Dispatch plan saved", dispatchService.savePlan(id, request));
    }

    @Operation(summary = "Officers of the unit owning the chosen (ward) station, with online / busy status (step 2)",
            description = "Only the officer holding the incident. Role required: OFFICER, ADMIN")
    @GetMapping("/candidates")
    public ResponseEntity<Object> getCandidates(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Delegation candidates", dispatchService.getCandidates(id));
    }

    @Operation(summary = "Hand the dispatch to an officer of that unit (step 2 \"Lập tức liên hệ\")",
            description = "Only the officer holding the incident, while the plan waits for acceptance. "
                    + "The incident shows in that ward unit's queue. Role required: OFFICER, ADMIN")
    @PostMapping("/delegate")
    public ResponseEntity<Object> delegate(@PathVariable String id, @RequestBody @Valid DelegateRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Delegated", dispatchService.delegate(id, request.getMemberId()));
    }

    @Operation(summary = "Withdraw a delegation that has not been answered yet (back to choosing in step 2)",
            description = "Only the officer holding the incident, while the plan waits for acceptance. Role required: OFFICER, ADMIN")
    @PostMapping("/withdraw")
    public ResponseEntity<Object> withdraw(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Withdrawn", dispatchService.withdraw(id));
    }

    @Operation(summary = "The delegated officer accepts", description = "Role required: OFFICER (the assigned officer)")
    @PostMapping("/accept")
    public ResponseEntity<Object> accept(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Accepted", dispatchService.accept(id));
    }

    @Operation(summary = "The delegated officer refuses, with a reason (5-255 characters)",
            description = "The plan goes back to step 1 for the delegating officer. Role required: OFFICER (the assigned officer)")
    @PostMapping("/reject")
    public ResponseEntity<Object> reject(@PathVariable String id, @RequestBody @Valid RejectDelegationRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Rejected", dispatchService.reject(id, request.getReason()));
    }

    @Operation(summary = "The delegated unit sets off (\"Lập tức điều động\"): step 3 tracking",
            description = "Body { member_id }: the field officer of my unit (carries the GPS phone, needs a badge number, "
                    + "not on another open trip; online not required). Role required: OFFICER (the assigned officer, after accepting)")
    @PostMapping("/depart")
    public ResponseEntity<Object> depart(@PathVariable String id, @RequestBody @Valid FieldMemberRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Departed", dispatchService.depart(id, request.getMemberId()));
    }

    @Operation(summary = "Officers of the unit that goes, to pick the field officer (người phụ trách hiện trường)",
            description = "The officer holding the incident when their own unit goes, or the assigned officer of the "
                    + "delegated unit. selectable=false + unavailable_reason: no badge number / on another open trip. "
                    + "Role required: OFFICER, ADMIN")
    @GetMapping("/field-candidates")
    public ResponseEntity<Object> getFieldCandidates(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Field officer candidates", dispatchService.getFieldCandidates(id));
    }

    @Operation(summary = "Change the field officer while the force is on the way (EN_ROUTE)",
            description = "Same rules as choosing it; a running simulation stops. Role required: OFFICER, ADMIN")
    @PutMapping("/field-member")
    public ResponseEntity<Object> changeFieldMember(@PathVariable String id, @RequestBody @Valid FieldMemberRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Field officer changed",
                dispatchService.changeFieldMember(id, request.getMemberId()));
    }

    @Operation(summary = "\"Khởi hành ngay\" in step 3: the force is at the scene -> step 4",
            description = "The assigned officer (delegated unit), or the officer holding the incident when their own unit went. "
                    + "Role required: OFFICER, ADMIN")
    @PostMapping("/arrive")
    public ResponseEntity<Object> arrive(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Arrived", dispatchService.arrive(id));
    }

    @Operation(summary = "DEMO ONLY: simulate the field officer's phone driving along the route",
            description = "Sends a GPS point every 5 s along the route (through Traccar when app.traccar.osmand-url is set), "
                    + "stops 30 m before the scene or when the trip is no longer EN_ROUTE; the trip status never changes. "
                    + "Only while the 'Ca demo' switch is on (or for [DEMO] incidents), the trip is EN_ROUTE and the field "
                    + "officer has a badge number; 403 / 409 otherwise. Role required: OFFICER, ADMIN (can view the incident)")
    @PostMapping("/simulate")
    public ResponseEntity<Object> startSimulation(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Simulation started", dispatchService.startSimulation(id));
    }

    @Operation(summary = "DEMO ONLY: stop the movement simulation", description = "Role required: OFFICER, ADMIN")
    @PostMapping("/simulate/stop")
    public ResponseEntity<Object> stopSimulation(@PathVariable String id) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Simulation stopped", dispatchService.stopSimulation(id));
    }

    @Operation(summary = "Step 4: confirm the incident is handled (both sides must confirm)",
            description = "The unit on site (JWT: assigned officer, or holder of own unit), or the citizen "
                    + "(header X-SOS-Access-Token). Role required: OFFICER, ADMIN or citizen token")
    @PostMapping("/confirm")
    public ResponseEntity<Object> confirm(@PathVariable String id,
                                          @RequestHeader(value = IncidentController.CITIZEN_TOKEN_HEADER, required = false) String citizenToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Confirmed", dispatchService.confirm(id, citizenToken));
    }

    @Operation(summary = "Close the incident with its report (-> RESOLVED)",
            description = "Normal: both confirmed, result RESOLVED / FALSE_ALARM, summary >= 10 characters. "
                    + "force=true: the officer holding it ends it at any step, note = reason (>= 5). Role required: OFFICER, ADMIN")
    @PostMapping("/finish")
    public ResponseEntity<Object> finish(@PathVariable String id, @RequestBody @Valid FinishIncidentRequest request) {
        dispatchService.finish(id, request);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Đã kết thúc ca");
    }
}
