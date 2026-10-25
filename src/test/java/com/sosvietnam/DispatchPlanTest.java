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
    final com.sosvietnam.service.DispatchSimulationService simulation = mock(com.sosvietnam.service.DispatchSimulationService.class);
    final com.sosvietnam.service.DemoModeService demo = mock(com.sosvietnam.service.DemoModeService.class);
    final DispatchServiceImpl service = new DispatchServiceImpl(incidents, dispatches, routes, stations, members,
            new com.sosvietnam.security.CurrentOfficer(), notifier,
            officers, claims, reports, new com.sosvietnam.service.implement.DispatchTrackingServiceImpl(dispatches),
            simulation, demo);

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
        when(members.findById(any())).thenAnswer(i -> Optional.ofNullable(known.get((UUID) i.getArgument(0))).map(TestGeo::member));
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

    DispatchPlanRequest request(boolean delegate) {
        return DispatchPlanRequest.builder().stationId(7L).delegate(delegate)
                .destination(new DispatchPlanRequest.Destination(10.028, 105.758, "02 Nguyễn Tri Phương", DestinationSource.OFFICER_EDIT))
                .route(new DispatchPlanRequest.Route(2700, 180, null, "abc", RouteProvider.OSRM))
                .build();
    }

    @Test
    void ownStation_goesToTracking_andStoresRouteAndDestination() {
        when(dispatches.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());

        DispatchPlanResponse plan = service.savePlan("SOS-1", request(false));

        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(plan.getStep()).isEqualTo(3);
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
                .role(DispatchRole.PRIMARY).status(DispatchStatus.EN_ROUTE).build();
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
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);

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

    @Test
    void arrive_ownUnit_holderMovesToStep4() {
        IncidentDispatch d = pending();
        d.setStatus(DispatchStatus.EN_ROUTE); // chính đơn vị mình đi (không có người được giao)
        DispatchPlanResponse plan = service.arrive("SOS-1");
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        assertThat(plan.getStep()).isEqualTo(4);
        assertThat(d.getArrivedAt()).isNotNull();
    }

    @Test
    void assignee_accepts_thenDeparts_reachesTracking() {
        IncidentDispatch d = pending();
        Account assignee = wardOfficer("ha@sos.vn");
        d.setAssignedTo(TestGeo.member(assignee));
        TestGeo.signIn(assignee);

        assertThat(service.accept("SOS-1").getStatus()).isEqualTo(DispatchStatus.ACCEPTED);
        assertThat(d.getRespondedAt()).isNotNull();
        DispatchPlanResponse plan = service.depart("SOS-1");
        assertThat(plan.getStep()).isEqualTo(3);
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
        assertThatThrownBy(() -> service.depart("SOS-1")).isInstanceOf(SosException.class);
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
    void finish_needsBothConfirmations_thenResolves() {
        IncidentDispatch d = atScene();
        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));

        assertThatThrownBy(() -> service.finish("SOS-1", report(ReportResult.RESOLVED, "Đã giải tán nhóm gây rối")))
                .isInstanceOf(SosException.class).hasMessageContaining("Cần cả cán bộ và người dân");

        service.confirm("SOS-1", null);   // cán bộ (người giữ ca, đơn vị mình đi)
        assertThat(incident.getOfficerConfirmedAt()).isNotNull();
        service.confirm("SOS-1", "tok");  // người dân
        assertThat(incident.getCitizenConfirmedAt()).isNotNull();

        service.finish("SOS-1", report(ReportResult.RESOLVED, "Đã giải tán nhóm gây rối"));
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(d.getStatus()).isEqualTo(DispatchStatus.DONE);
        verify(reports).save(org.mockito.ArgumentMatchers.argThat(rep -> rep.getResult() == ReportResult.RESOLVED));
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_REMOVED);
    }

    @Test
    void finishNow_skipConfirm_onlyInDemoMode() {
        atScene();
        var req = report(ReportResult.RESOLVED, "Đã xử lý xong vụ việc");
        req.setSkipConfirm(true);
        assertThatThrownBy(() -> service.finish("SOS-1", req))
                .isInstanceOf(SosException.class).hasMessageContaining("chế độ test");

        org.springframework.test.util.ReflectionTestUtils.setField(service, "demoMode", true);
        service.finish("SOS-1", req); // chưa ai xác nhận vẫn kết thúc được
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
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
    void forceFinish_onlyHolder_withReason_onceForcesAreOnTheWay() {
        var force = com.sosvietnam.model.payload.request.FinishIncidentRequest.builder().force(true)
                .note("Kết thúc để test lại").build();
        IncidentDispatch d = pending(); // chưa xuất phát -> chưa kết thúc được, dùng Huỷ ca
        assertThatThrownBy(() -> service.finish("SOS-1", force))
                .isInstanceOf(SosException.class).hasMessageContaining("Huỷ ca");

        d.setStatus(DispatchStatus.EN_ROUTE);
        force.setNote("ab");
        assertThatThrownBy(() -> service.finish("SOS-1", force))
                .isInstanceOf(SosException.class).hasMessageContaining("lý do");

        force.setNote("Kết thúc để test lại");
        service.finish("SOS-1", force);
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        verify(reports).save(org.mockito.ArgumentMatchers.argThat(rep -> rep.getResult() == ReportResult.FORCED));

        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setHandlerId(UUID.randomUUID()); // người khác giữ
        assertThatThrownBy(() -> service.finish("SOS-1", force))
                .isInstanceOf(SosException.class).hasMessageContaining("đang giữ ca");
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
}
