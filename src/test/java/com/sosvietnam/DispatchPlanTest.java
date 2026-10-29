package com.sosvietnam;

import com.sosvietnam.model.entity.*;
import com.sosvietnam.model.payload.enums.*;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.DispatchPlanRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.repository.*;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.DispatchServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** PUT / GET /incidents/{id}/dispatch-plan: chỉ người giữ ca RESOLVING, tuyến cũ giữ lại, dân xem bằng token. */
class DispatchPlanTest {

    final IncidentRepository incidents = mock(IncidentRepository.class);
    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final IncidentRouteRepository routes = mock(IncidentRouteRepository.class);
    final RescueStationRepository stations = mock(RescueStationRepository.class);
    final com.sosvietnam.repository.UnitMemberRepository members = mock(com.sosvietnam.repository.UnitMemberRepository.class);
    // cán bộ mà members.findById / getReferenceById trả về (id cán bộ = id tài khoản trong test)
    final java.util.Map<UUID, Account> known = new java.util.HashMap<>();
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final com.sosvietnam.service.OfficerService officers = mock(com.sosvietnam.service.OfficerService.class);
    final IncidentClaimRepository claims = mock(IncidentClaimRepository.class);
    final IncidentReportRepository reports = mock(IncidentReportRepository.class);
    final com.sosvietnam.service.RoutingService routing = mock(com.sosvietnam.service.RoutingService.class);
    final com.sosvietnam.service.implement.DispatchTrackingServiceImpl tracking =
            new com.sosvietnam.service.implement.DispatchTrackingServiceImpl(dispatches, mock(com.sosvietnam.service.RouteRecalcService.class));
    final com.sosvietnam.service.DispatchSimulationService simulation = mock(com.sosvietnam.service.DispatchSimulationService.class);
    final DispatchServiceImpl service = new DispatchServiceImpl(incidents, dispatches, routes, stations, members,
            new com.sosvietnam.security.CurrentOfficer(), notifier,
            officers, claims, reports, tracking,
            simulation, routing, mock(com.sosvietnam.service.SmsService.class));

    final Role officerRole = Role.builder().id(2L).roleName("OFFICER").build();
    Account me;
    Incident incident;
    RescueStation station;

    @BeforeEach
    void setUp() {
        me = Account.builder().id(UUID.randomUUID()).email("me@sos.vn").firstName("An").lastName("Cán Bộ").role(officerRole)
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(TestGeo.ward("Phường Tân An")).isActive(true).build();
        incident = Incident.builder().id("SOS-1").status(IncidentStatus.RESOLVING).handlerId(me.getId())
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD).emergencyType(EmergencyType.POLICE)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An"))
                .latitude(10.0276).longitude(105.7574).build();
        station = RescueStation.builder().id(7L).name("Công An Phường Tân An").agencyType(AgencyType.POLICE)
                .level(StationLevel.WARD).latitude(10.03).longitude(105.76).address("02 Nguyễn Tri Phương").build();

        known.put(me.getId(), me);
        when(members.findById(any())).thenAnswer(i -> Optional.ofNullable(known.get((UUID) i.getArgument(0))).map(DispatchPlanTest::withBadge));
        when(members.getReferenceById(any())).thenAnswer(i -> TestGeo.member(known.get((UUID) i.getArgument(0))));
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));
        when(stations.findById(7L)).thenReturn(Optional.of(station));
        when(dispatches.save(any())).thenAnswer(inv -> {
            IncidentDispatch d = inv.getArgument(0);
            if (d.getId() == null) d.setId(11L);
            return d;
        });
        when(routes.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestGeo.signIn(me);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Cán bộ có số hiệu (= mã thiết bị GPS) để làm người phụ trách hiện trường. */
    static UnitMember withBadge(Account a) {
        UnitMember m = TestGeo.member(a);
        m.setBadgeNumber("B" + Math.abs(a.getId().hashCode() % 100000));
        return m;
    }

    DispatchPlanRequest request(boolean delegate) {
        return DispatchPlanRequest.builder().stationId(7L).delegate(delegate)
                .fieldMemberId(delegate ? null : me.getId())
                .destination(new DispatchPlanRequest.Destination(10.028, 105.758, "02 Nguyễn Tri Phương", DestinationSource.OFFICER_EDIT))
                .route(new DispatchPlanRequest.Route(2700, 180, null, "abc", RouteProvider.OSRM))
                .build();
    }

    @Test
    void ownStation_personChosen_waitsForDeparture_andStoresRouteAndDestination() {
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());

        DispatchPlanResponse plan = service.savePlan("SOS-1", request(false));

        // đã chọn người phụ trách nhưng chưa "Khởi hành": vẫn bước 2, chưa nhận GPS
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(plan.getStatusLabel()).contains("chờ khởi hành");
        assertThat(plan.getStep()).isEqualTo(2);
        assertThat(plan.getStation().getName()).isEqualTo("Công An Phường Tân An");
        assertThat(plan.getRoute().getDistanceM()).isEqualTo(2700);
        assertThat(plan.getRoute().getOrigin().getLatitude()).isEqualTo(10.03); // điểm đi = toạ độ trạm
        assertThat(incident.getDestLatitude()).isEqualTo(10.028);
        assertThat(incident.getDestUpdatedBy()).isEqualTo(me.getId());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void wardStation_needsDelegation_andOldRouteIsSuperseded() {
        IncidentDispatch existing = IncidentDispatch.builder().id(11L).incident(incident).station(station)
                .role(DispatchRole.PRIMARY).status(DispatchStatus.ACCEPTED).build(); // chưa khởi hành: đổi được
        IncidentRoute old = IncidentRoute.builder().id(1L).dispatch(existing).build();
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.of(existing));
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.of(old));

        DispatchPlanResponse plan = service.savePlan("SOS-1", request(true));

        assertThat(plan.getStep()).isEqualTo(2);
        assertThat(plan.isNeedsDelegation()).isTrue();
        assertThat(old.getSupersededAt()).isNotNull();
        verify(routes, times(2)).save(any()); // cũ (đánh dấu) + mới
    }

    @Test
    void notHolder_403_and_notResolving_409() {
        incident.setHandlerId(UUID.randomUUID());
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("đang giữ ca");
        incident.setHandlerId(me.getId());
        incident.setStatus(IncidentStatus.VERIFYING);
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("Yêu cầu điều động");
    }

    @Test
    void otherAgencyStation_400() {
        station.setAgencyType(AgencyType.FIRE);
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("lực lượng");
    }

    // ---------------------------------------------------------------- bước 2 uỷ quyền

    Account wardOfficer(String email) {
        Account a = Account.builder().id(UUID.randomUUID()).email(email).firstName("Hà").lastName("Lê Thu").role(officerRole)
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(station.getWard()).isActive(true).build();
        known.put(a.getId(), a);
        return a;
    }

    IncidentDispatch pending() {
        IncidentDispatch d = IncidentDispatch.builder().id(11L).incident(incident).station(station)
                .role(DispatchRole.PRIMARY).status(DispatchStatus.PENDING_ACCEPT)
                .dispatchedAt(java.time.LocalDateTime.now()).build();
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.of(d));
        when(dispatches.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(any(), any())).thenReturn(Optional.of(d));
        return d;
    }

    @Test
    void afterDeparture_noRedelegation_noReset() {
        IncidentDispatch d = pending();
        UnitMember field = withBadge(me);
        d.setFieldMember(field);
        d.setStatus(DispatchStatus.EN_ROUTE);

        // đơn vị mình đang đi: uỷ quyền lại -> 409; đổi điểm đến (không uỷ quyền) vẫn được, giữ chuyến
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(true)))
                .isInstanceOf(SosException.class).hasMessageContaining("đã khởi hành");
        assertThat(service.savePlan("SOS-1", request(false)).getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);

        // lượt uỷ quyền xã đã đi: không uỷ quyền lại, không đổi sang đơn vị mình
        d.setStatus(DispatchStatus.EN_ROUTE);
        d.setAssignedTo(TestGeo.member(wardOfficer("xa@sos.vn")));
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(true)))
                .isInstanceOf(SosException.class).hasMessageContaining("đã khởi hành");
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("đã khởi hành");

        // đã tới nơi: không sửa kế hoạch nữa
        d.setAssignedTo(null);
        d.setStatus(DispatchStatus.ARRIVED);
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("đã khởi hành");
        // delegate (chỉ định người) cũng bị chặn vì không còn chờ chấp nhận
        assertThatThrownBy(() -> service.delegate("SOS-1", me.getId())).isInstanceOf(SosException.class);
    }

    @Test
    void delegate_onlyToOfficerOfTheStationsWard() {
        station.setWard(TestGeo.ward("Phường Tân An"));
        IncidentDispatch d = pending();
        Account target = wardOfficer("x@sos.vn");

        DispatchPlanResponse plan = service.delegate("SOS-1", target.getId());
        assertThat(d.getAssignedTo().getId()).isEqualTo(target.getId());
        assertThat(plan.getAssignedTo().getFullName()).isEqualTo(target.fullName());

        d.setAssignedTo(null); // (đổi người chỉ khi chưa giao: xem test khoá bên dưới)
        Account stranger = wardOfficer("y@sos.vn");
        stranger.setWard(TestGeo.ward("Phường Cái Khế"));
        assertThatThrownBy(() -> service.delegate("SOS-1", stranger.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("không thuộc đơn vị");
    }

    @Test
    void waitingForAnswer_locksPlanAndRecipient() {
        IncidentDispatch d = pending();
        d.setAssignedTo(TestGeo.member(wardOfficer("ha@sos.vn")));

        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("Đang chờ");
        Account other = wardOfficer("khoa@sos.vn");
        assertThatThrownBy(() -> service.delegate("SOS-1", other.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("Đang chờ");
    }

    @Test
    void delegate_toOfficerBusyWithAnotherCase_409() {
        station.setWard(TestGeo.ward("Phường Tân An"));
        pending();
        Account busy = wardOfficer("busy@sos.vn");
        when(incidents.existsByHandlerIdAndStatusIn(eq(busy.getId()), any())).thenReturn(true);
        assertThatThrownBy(() -> service.delegate("SOS-1", busy.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("đang trong ca khác");

        Account delegatedElsewhere = wardOfficer("other@sos.vn");
        Incident otherCase = Incident.builder().id("SOS-2").status(IncidentStatus.RESOLVING).build();
        IncidentDispatch elsewhere = IncidentDispatch.builder().id(22L).incident(otherCase).station(station)
                .role(DispatchRole.PRIMARY).status(DispatchStatus.EN_ROUTE).assignedTo(TestGeo.member(delegatedElsewhere)).build();
        when(dispatches.findActiveAssignments(eq(List.of(delegatedElsewhere.getId())), any(), any()))
                .thenReturn(List.of(elsewhere));
        assertThatThrownBy(() -> service.delegate("SOS-1", delegatedElsewhere.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("đang trong ca khác");
    }

    @Test
    void withdraw_unansweredDelegation_unlocksThePlan() {
        IncidentDispatch d = pending();
        d.setAssignedTo(TestGeo.member(wardOfficer("ha@sos.vn")));
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("Đang chờ");

        DispatchPlanResponse plan = service.withdraw("SOS-1");
        assertThat(d.getAssignedTo()).isNull();
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.PENDING_ACCEPT);
        assertThat(plan.getAssignedTo()).isNull();
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);

        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());
        service.savePlan("SOS-1", request(false)); // mở khoá: đổi sang trạm của mình được
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.ACCEPTED); // chờ khởi hành

        assertThatThrownBy(() -> service.withdraw("SOS-1")) // không còn gì để thu hồi
                .isInstanceOf(SosException.class).hasMessageContaining("thu hồi");
    }

    @Test
    void delegate_assignmentOfClosedCaseDoesNotBlock() {
        // Ca cũ đã huỷ: findActiveAssignments chỉ trả lượt của ca còn mở -> rỗng -> giao được
        station.setWard(TestGeo.ward("Phường Tân An"));
        IncidentDispatch d = pending();
        Account free = wardOfficer("free@sos.vn");
        when(dispatches.findActiveAssignments(any(), any(), any())).thenReturn(List.of());
        service.delegate("SOS-1", free.getId());
        assertThat(d.getAssignedTo().getId()).isEqualTo(free.getId());
    }

    /** Điểm đến của lượt 11 = (10.0281, 105.7579). */
    void destinationAt(IncidentDispatch d) {
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.of(
                IncidentRoute.builder().id(5L).dispatch(d).destLat(10.0281).destLng(105.7579).build()));
    }

    /** Một điểm Traccar của người đi (số hiệu 123) cho lượt d. */
    void gps(IncidentDispatch d, double lat, double lng, Double accuracy) {
        when(dispatches.findEnRouteByTrackerBadge("123")).thenReturn(List.of(d));
        tracking.accept(com.sosvietnam.model.payload.request.TraccarPositionRequest.builder()
                .device(com.sosvietnam.model.payload.request.TraccarPositionRequest.Device.builder().uniqueId("123").build())
                .position(com.sosvietnam.model.payload.request.TraccarPositionRequest.Position.builder()
                        .latitude(lat).longitude(lng).accuracy(accuracy).build())
                .build());
    }

    @Test
    void arrive_needsGpsWithin30mPlusAccuracy() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE); // chính đơn vị mình đi (không có người được giao)
        destinationAt(d);
        assertThatThrownBy(() -> service.arrive("SOS-1", null, null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Chưa có tín hiệu GPS");

        gps(d, 10.0285, 105.7579, 5.0); // ~44 m, sai số 5 m -> cần trong 35 m
        assertThatThrownBy(() -> service.arrive("SOS-1", null, null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Còn 44 m").hasMessageContaining("35 m");
        assertThat(service.getPlan("SOS-1", null).getArrivalCheck().isWithin()).isFalse();

        gps(d, 10.0285, 105.7579, 200.0); // sai số tính tối đa 50 m -> cần trong 80 m
        assertThat(service.getPlan("SOS-1", null).getArrivalCheck().getThresholdM()).isEqualTo(80);
        DispatchPlanResponse plan = service.arrive("SOS-1", null, null);
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        assertThat(plan.getStep()).isEqualTo(4);
        assertThat(d.getArrivalSource()).isEqualTo(com.sosvietnam.model.payload.enums.ArrivalSource.GPS);
        assertThat(d.getArrivalDistanceM()).isEqualTo(44);
    }

    @Test
    void arrive_withoutGps_ownUnit_onlyTheHolderWithAReason() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        destinationAt(d);

        TestGeo.signIn(wardOfficer("other@sos.vn"));
        assertThatThrownBy(() -> service.arrive("SOS-1", null, "Điện thoại hết pin")).isInstanceOf(SosException.class)
                .hasMessageContaining("Chỉ người giữ ca");

        TestGeo.signIn(me);
        assertThatThrownBy(() -> service.arrive("SOS-1", null, "pin")).isInstanceOf(SosException.class)
                .hasMessageContaining("5 ký tự");
        DispatchPlanResponse plan = service.arrive("SOS-1", null, "  Điện thoại hết pin  ");
        assertThat(plan.getArrivalSource()).isEqualTo(com.sosvietnam.model.payload.enums.ArrivalSource.HOLDER_OVERRIDE);
    }

    @Test
    void arrive_withoutGps_delegated_theAssigneeNotTheProvinceHolder() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        Account assignee = wardOfficer("ha@sos.vn");
        d.setAssignedTo(TestGeo.member(assignee));
        destinationAt(d);

        TestGeo.signIn(me); // tỉnh đã uỷ quyền: người giữ ca không xác nhận thay
        assertThatThrownBy(() -> service.arrive("SOS-1", null, "Điện thoại hết pin")).isInstanceOf(SosException.class)
                .hasMessageContaining("Ca đã uỷ quyền");

        TestGeo.signIn(assignee);
        DispatchPlanResponse plan = service.arrive("SOS-1", null, "  Điện thoại hết pin  ");
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        assertThat(plan.getArrivalSource()).isEqualTo(com.sosvietnam.model.payload.enums.ArrivalSource.HOLDER_OVERRIDE);
        assertThat(plan.getArrivalNote()).isEqualTo("Điện thoại hết pin");
        assertThat(d.getArrivalDistanceM()).isNull();
    }

    @Test
    void arrive_keepsTheLastGpsPoint_andStopsTheSimulation() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        destinationAt(d);
        gps(d, 10.0281, 105.7579, null);

        DispatchPlanResponse plan = service.arrive("SOS-1", null, null);
        assertThat(plan.getLastPosition()).isNotNull(); // chấm xe vẫn ở điểm cuối sau khi "Đã đến"
        assertThat(plan.getLastPosition().getLatitude()).isEqualTo(10.0281);
        verify(simulation).stop(11L);
        assertThat(service.getPlan("SOS-1", null).getLastPosition()).isNotNull();
    }

    @Test
    void ownUnit_step2_holderPicksTheFieldOfficer_thenSetsOff() {
        incident.setStatus(IncidentStatus.VERIFYING);
        service.checkFieldMember("SOS-1", me.getId()); // cán bộ đơn vị mình, có số hiệu: hợp lệ
        incident.setHandlerId(UUID.randomUUID());
        assertThatThrownBy(() -> service.checkFieldMember("SOS-1", me.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("không giữ ca");

        incident.setHandlerId(me.getId());
        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setFieldMemberId(me.getId()); // chọn lúc "Yêu cầu điều động"
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());
        DispatchPlanRequest noPick = request(false);
        noPick.setFieldMemberId(null);
        DispatchPlanResponse step2 = service.savePlan("SOS-1", noPick);
        assertThat(step2.getStatus()).isEqualTo(DispatchStatus.ACCEPTED); // chờ chọn người, không tự đi

        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.ACCEPTED);
        DispatchPlanResponse off = service.depart("SOS-1", me.getId());
        assertThat(off.getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(off.getTracker().getMemberId()).isEqualTo(me.getId());
        assertThat(d.getDepartedAt()).isNotNull();

        // người khác (không giữ ca, không phải người đi) không chọn / khởi hành thay được
        d.setStatus(DispatchStatus.ACCEPTED);
        TestGeo.signIn(wardOfficer("khac2@sos.vn"));
        assertThatThrownBy(() -> service.depart("SOS-1", me.getId())).isInstanceOf(SosException.class)
                .hasMessageContaining("Chỉ người");
        assertThatThrownBy(() -> service.depart("SOS-1", null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Chỉ người");
    }

    /** Chọn người (PUT /field-member) không xuất phát; "Khởi hành" mới EN_ROUTE, chưa chọn người thì 409. */
    @Test
    void ownUnit_choosingThePerson_doesNotSetOff_departDoes() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.ACCEPTED);
        assertThatThrownBy(() -> service.depart("SOS-1", null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Chưa chọn người");

        DispatchPlanResponse chosen = service.changeFieldMember("SOS-1", me.getId());
        assertThat(chosen.getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(chosen.getStatusLabel()).contains("chờ khởi hành");
        assertThat(d.getDepartedAt()).isNull();
        assertThat(service.getPlan("SOS-1", null).getSimulation().isAvailable()).isFalse(); // chưa đi: chưa mô phỏng

        DispatchPlanResponse off = service.depart("SOS-1", null);
        assertThat(off.getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(d.getDepartedAt()).isNotNull();
        assertThatThrownBy(() -> service.depart("SOS-1", null)).isInstanceOf(SosException.class)
                .hasMessageContaining("chờ khởi hành");
    }

    @Test
    void fireOrCsgt_appointedOfficerIsTheFieldOfficer_andSetsOffAtOnce() {
        station.setAgencyType(AgencyType.FIRE);
        station.setWard(TestGeo.ward("Phường Tân An"));
        IncidentDispatch d = pending();
        Account team = wardOfficer("doi.pccc@sos.vn");
        team.setAgencyType(AgencyType.FIRE);

        DispatchPlanResponse plan = service.delegate("SOS-1", team.getId());
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ACCEPTED); // không chờ Chấp nhận, chờ khởi hành
        assertThat(d.getAssignedTo().getId()).isEqualTo(team.getId());
        assertThat(d.getFieldMember().getId()).isEqualTo(team.getId()); // người được chỉ định = người cầm GPS
        assertThat(d.getDepartedAt()).isNull();

        // người giữ ca (đã chỉ định) bấm "Khởi hành"
        assertThat(service.depart("SOS-1", null).getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(d.getDepartedAt()).isNotNull();
    }

    @Test
    void arrive_byTheCitizen_isEnough_wrongToken403() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));
        assertThatThrownBy(() -> service.arrive("SOS-1", "sai", null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Mã truy cập");
        // dân xác nhận: không cần GPS
        assertThat(service.arrive("SOS-1", "tok", null).getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        assertThat(d.getArrivalSource()).isEqualTo(com.sosvietnam.model.payload.enums.ArrivalSource.CITIZEN);
        // đã tới rồi thì bấm lại -> 409
        assertThatThrownBy(() -> service.arrive("SOS-1", "tok", null)).isInstanceOf(SosException.class)
                .hasMessageContaining("chưa ở bước đang di chuyển");
    }

    @Test
    void assignee_accepts_thenDeparts_reachesTracking() {
        IncidentDispatch d = pending();
        Account assignee = wardOfficer("ha@sos.vn");
        d.setAssignedTo(TestGeo.member(assignee));
        TestGeo.signIn(assignee);

        assertThat(service.accept("SOS-1").getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(d.getRespondedAt()).isNotNull();
        DispatchPlanResponse plan = service.depart("SOS-1", assignee.getId());
        assertThat(plan.getStep()).isEqualTo(3);
        assertThat(plan.getTracker().getMemberId()).isEqualTo(assignee.getId()); // người phụ trách hiện trường
        assertThat(d.getDepartedAt()).isNotNull();
        verify(notifier, times(2)).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void reject_keepsReason_andOnlyTheAssigneeMayAnswer() {
        IncidentDispatch d = pending();
        Account assignee = wardOfficer("ha@sos.vn");
        d.setAssignedTo(TestGeo.member(assignee));
        // người giữ ca (me) không phải người được giao
        assertThatThrownBy(() -> service.accept("SOS-1"))
                .isInstanceOf(SosException.class).hasMessageContaining("không được giao cho bạn");

        TestGeo.signIn(assignee);
        DispatchPlanResponse plan = service.reject("SOS-1", "  Đơn vị đang xử lý vụ khác  ");
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.REJECTED);
        assertThat(plan.getStep()).isEqualTo(1);
        assertThat(plan.getRejectReason()).isEqualTo("Đơn vị đang xử lý vụ khác");
        // đã từ chối thì không xuất phát được
        assertThatThrownBy(() -> service.depart("SOS-1", assignee.getId())).isInstanceOf(SosException.class);
    }

    // ---------------------------------------------------------------- bước 4 kết thúc

    IncidentDispatch atScene() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.ARRIVED); // chính đơn vị mình đi, đã tới nơi
        return d;
    }

    com.sosvietnam.model.payload.request.FinishIncidentRequest report(ReportResult result, String summary) {
        return com.sosvietnam.model.payload.request.FinishIncidentRequest.builder()
                .result(result).summary(summary).forces("2 cán bộ, 1 xe").build();
    }

    @Test
    void officerFirst_thenTheCitizen_closes() {
        IncidentDispatch d = atScene();
        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));
        // cán bộ lập văn bản "Đã xử lý xong" trước: ghi văn bản + xác nhận, ca chưa đóng
        assertThat(service.finish("SOS-1", report(ReportResult.RESOLVED, "Đã giải tán nhóm gây rối"))).isFalse();
        assertThat(incident.getOfficerConfirmedAt()).isNotNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVING);
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        org.mockito.ArgumentCaptor<IncidentReport> draft = org.mockito.ArgumentCaptor.forClass(IncidentReport.class);
        verify(reports).save(draft.capture());

        // dân xác nhận sau -> đủ hai bên, đóng ca bằng văn bản của cán bộ
        when(reports.findById("SOS-1")).thenReturn(Optional.of(draft.getValue()));
        service.confirm("SOS-1", "tok");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DONE);
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_REMOVED);
    }

    @Test
    void falseAlarm_closesAtOnce_withoutTheCitizen() {
        IncidentDispatch d = atScene();
        assertThat(service.finish("SOS-1", report(ReportResult.FALSE_ALARM, "Không có sự việc như báo tin"))).isTrue();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getCitizenConfirmedAt()).isNull();
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DONE);
        verify(reports).save(org.mockito.ArgumentMatchers.argThat(rep -> rep.getResult() == ReportResult.FALSE_ALARM));
    }

    @Test
    void testCase_closesAtOnce_anyStep_noTextNeeded() {
        IncidentDispatch d = pending(); // chưa tới nơi vẫn đóng được
        var test = com.sosvietnam.model.payload.request.FinishIncidentRequest.builder().result(ReportResult.TEST).build();
        assertThat(service.finish("SOS-1", test)).isTrue();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        verify(reports).save(org.mockito.ArgumentMatchers.argThat(rep -> rep.getResult() == ReportResult.TEST
                && "Ca test".equals(rep.getSummary())));
    }

    @Test
    void citizenConfirm_onlyRecords_thenTheOfficerFinishes() {
        IncidentDispatch d = atScene();
        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));
        service.confirm("SOS-1", "tok");
        assertThat(incident.getCitizenConfirmedAt()).isNotNull();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVING);
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        verify(reports, never()).save(any());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);

        assertThat(service.finish("SOS-1", report(ReportResult.RESOLVED, "Đã giải tán nhóm gây rối"))).isTrue();
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(incident.getOfficerConfirmedAt()).isNotNull();
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DONE);
        verify(reports).save(org.mockito.ArgumentMatchers.argThat(rep -> rep.getResult() == ReportResult.RESOLVED));
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_REMOVED);
    }

    @Test
    void confirm_beforeArriving_409_andShortSummary_400() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        assertThatThrownBy(() -> service.confirm("SOS-1", null))
                .isInstanceOf(SosException.class).hasMessageContaining("chưa tới hiện trường");

        d.setStatus(DispatchStatus.ARRIVED);
        incident.setOfficerConfirmedAt(java.time.LocalDateTime.now());
        incident.setCitizenConfirmedAt(java.time.LocalDateTime.now());
        assertThatThrownBy(() -> service.finish("SOS-1", report(ReportResult.RESOLVED, "ngắn")))
                .isInstanceOf(SosException.class).hasMessageContaining("ít nhất 10");
    }

    @Test
    void forceFinish_isGone_400_caseStaysOpen() {
        var force = com.sosvietnam.model.payload.request.FinishIncidentRequest.builder().force(true)
                .note("Kết thúc để test lại").build();
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        assertThatThrownBy(() -> service.finish("SOS-1", force))
                .isInstanceOf(SosException.class).hasMessageContaining("Không còn kết thúc ca bắt buộc");
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVING);
        verify(reports, never()).save(any());
    }

    @Test
    void citizenReadsWithToken_wrongToken403() {
        SecurityContextHolder.clearContext();
        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThat(service.getPlan("SOS-1", "tok")).isNull(); // chưa chọn trạm
        assertThatThrownBy(() -> service.getPlan("SOS-1", "sai"))
                .isInstanceOf(SosException.class).hasMessageContaining("không hợp lệ");
    }

    // ---------------------------------------------------------------- người phụ trách hiện trường (25/10/2026)

    @Test
    void ownUnit_needsAFieldOfficerOfMyUnit_withBadge() {
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());

        // chưa chọn người: bước 2 "Điều động" chờ chọn người phụ trách (ACCEPTED), chưa xuất phát
        DispatchPlanRequest none = request(false);
        none.setFieldMemberId(null);
        DispatchPlanResponse waiting = service.savePlan("SOS-1", none);
        assertThat(waiting.getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(waiting.getTracker()).isNull();

        DispatchPlanRequest stranger = request(false);
        stranger.setFieldMemberId(wardOfficer("other.unit@sos.vn").getId()); // tài khoản đơn vị khác
        assertThatThrownBy(() -> service.savePlan("SOS-1", stranger))
                .isInstanceOf(SosException.class).hasMessageContaining("cán bộ của đơn vị bạn");

        UnitMember colleague = withBadge(me);
        colleague.setId(UUID.randomUUID()); // cùng tài khoản đơn vị, không trực tuyến cũng được
        when(members.findById(colleague.getId())).thenReturn(Optional.of(colleague));
        DispatchPlanRequest ok = request(false);
        ok.setFieldMemberId(colleague.getId());
        DispatchPlanResponse plan = service.savePlan("SOS-1", ok);
        assertThat(plan.getTracker().getMemberId()).isEqualTo(colleague.getId());
        assertThat(plan.getTracker().getBadgeNumber()).isEqualTo(colleague.getBadgeNumber());

        colleague.setBadgeNumber(" ");
        assertThatThrownBy(() -> service.savePlan("SOS-1", ok))
                .isInstanceOf(SosException.class).hasMessageContaining("số hiệu");
    }

    @Test
    void fieldOfficerOnAnotherOpenTrip_409() {
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        Incident other = Incident.builder().id("SOS-2").status(IncidentStatus.RESOLVING).build();
        when(dispatches.findActiveFieldTrips(eq(List.of(me.getId())), any(), any())).thenReturn(List.of(
                IncidentDispatch.builder().id(22L).incident(other).fieldMember(withBadge(me)).build()));
        assertThatThrownBy(() -> service.savePlan("SOS-1", request(false)))
                .isInstanceOf(SosException.class).hasMessageContaining("đang phụ trách ca SOS-2");
    }

    @Test
    void candidates_holderOrAssignee_markBusyAndNoBadge() {
        IncidentDispatch d = pending();
        UnitMember free = withBadge(me);
        UnitMember noBadge = TestGeo.member(me);
        noBadge.setId(UUID.randomUUID());
        when(members.findByAccount_IdAndActiveTrueOrderByFullNameAsc(me.getId())).thenReturn(List.of(free, noBadge));
        when(notifier.onlineMemberIds()).thenReturn(java.util.Set.of());

        var list = service.getFieldCandidates("SOS-1");
        assertThat(list).hasSize(2);
        assertThat(list.get(0).isSelectable()).isTrue();
        assertThat(list.get(0).isOnline()).isFalse(); // ngoại tuyến vẫn chọn được
        assertThat(list.get(1).isSelectable()).isFalse();
        assertThat(list.get(1).getUnavailableReason()).contains("số hiệu");

        // đã uỷ quyền cho xã: cán bộ được giao xem đơn vị của họ; người khác (không giữ ca, không được giao) 403
        Account assignee = wardOfficer("ha@sos.vn");
        d.setAssignedTo(TestGeo.member(assignee));
        TestGeo.signIn(wardOfficer("khac@sos.vn"));
        assertThatThrownBy(() -> service.getFieldCandidates("SOS-1"))
                .isInstanceOf(SosException.class).hasMessageContaining("người nhận ca");
        TestGeo.signIn(assignee);
        when(members.findByAccount_IdAndActiveTrueOrderByFullNameAsc(assignee.getId()))
                .thenReturn(List.of(withBadge(assignee)));
        assertThat(service.getFieldCandidates("SOS-1")).hasSize(1);
    }

    @Test
    void candidates_selectableFirst_onlineFirst_thenByName() {
        pending();
        UnitMember noBadge = TestGeo.member(me);
        noBadge.setId(UUID.randomUUID());
        noBadge.setFullName("An Không Số Hiệu");
        UnitMember offline = withBadge(me);
        offline.setId(UUID.randomUUID());
        offline.setFullName("Bình Ngoại Tuyến");
        UnitMember online = withBadge(me);
        online.setId(UUID.randomUUID());
        online.setFullName("Cường Trực Tuyến");
        // repository trả theo tên
        when(members.findByAccount_IdAndActiveTrueOrderByFullNameAsc(me.getId())).thenReturn(List.of(noBadge, offline, online));
        when(notifier.onlineMemberIds()).thenReturn(java.util.Set.of(online.getId(), noBadge.getId()));

        var list = service.getFieldCandidates("SOS-1");
        assertThat(list).extracting(c -> c.getMemberId()).containsExactly(online.getId(), offline.getId(), noBadge.getId());
    }

    @Test
    void changeFieldMember_onlyBeforeArrival_stopsSimulation() {
        IncidentDispatch d = pending();
        assertThatThrownBy(() -> service.changeFieldMember("SOS-1", me.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("đã nhận ca, chưa tới nơi");
        d.setStatus(DispatchStatus.EN_ROUTE);
        DispatchPlanResponse plan = service.changeFieldMember("SOS-1", me.getId());
        assertThat(plan.getTracker().getMemberId()).isEqualTo(me.getId());
        verify(simulation).stop(11L);
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void whileSimulating_changingFieldOfficer_is409() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE);
        when(simulation.running(11L)).thenReturn(true);
        assertThatThrownBy(() -> service.changeFieldMember("SOS-1", me.getId()))
                .isInstanceOf(SosException.class).hasMessageContaining("Đang mô phỏng");
        verify(simulation, never()).stop(11L);
    }
}
