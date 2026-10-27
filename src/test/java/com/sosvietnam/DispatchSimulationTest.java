package com.sosvietnam;

import com.sosvietnam.model.entity.*;
import com.sosvietnam.model.payload.enums.*;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.repository.*;
import com.sosvietnam.service.DispatchSimulationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.DispatchServiceImpl;
import com.sosvietnam.service.implement.DispatchTrackingServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.Polylines;
import com.sosvietnam.util.RouteSimulation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** "Mô phỏng" di chuyển (mọi ca, 25/10/2026): lượt EN_ROUTE, số hiệu; tuyến giải mã + chia bước đều. */
class DispatchSimulationTest {

    final IncidentRepository incidents = mock(IncidentRepository.class);
    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final IncidentRouteRepository routes = mock(IncidentRouteRepository.class);
    final DispatchSimulationService simulation = mock(DispatchSimulationService.class);
    final com.sosvietnam.service.RoutingService routing = mock(com.sosvietnam.service.RoutingService.class);
    final DispatchServiceImpl service = new DispatchServiceImpl(incidents, dispatches, routes,
            mock(RescueStationRepository.class), mock(UnitMemberRepository.class),
            new com.sosvietnam.security.CurrentOfficer(), mock(IncidentNotifier.class),
            mock(com.sosvietnam.service.OfficerService.class), mock(IncidentClaimRepository.class),
            mock(IncidentReportRepository.class), new DispatchTrackingServiceImpl(dispatches, mock(com.sosvietnam.service.RouteRecalcService.class)),
            simulation, routing);

    Incident incident;
    IncidentDispatch trip;
    UnitMember goer;

    @BeforeEach
    void setUp() {
        Account me = Account.builder().id(UUID.randomUUID()).email("me@sos.vn").firstName("An").lastName("Cán Bộ")
                .role(Role.builder().id(2L).roleName("OFFICER").build())
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(TestGeo.ward("Phường Tân An")).isActive(true).build();
        incident = Incident.builder().id("SOS-1").status(IncidentStatus.RESOLVING).handlerId(me.getId())
                .citizenName("Nguyễn Văn B")
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD).emergencyType(EmergencyType.POLICE)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An"))
                .latitude(10.0276).longitude(105.7574).build();
        RescueStation station = RescueStation.builder().id(7L).name("Công An Phường Tân An").agencyType(AgencyType.POLICE)
                .level(StationLevel.WARD).latitude(10.03).longitude(105.76).build();
        goer = TestGeo.member(me);
        goer.setBadgeNumber("594288");
        trip = IncidentDispatch.builder().id(11L).incident(incident).station(station).dispatchedBy(goer).fieldMember(goer)
                .role(DispatchRole.PRIMARY).status(DispatchStatus.EN_ROUTE).build();
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));
        when(dispatches.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc("SOS-1", DispatchRole.PRIMARY))
                .thenReturn(Optional.of(trip));
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());
        when(simulation.stepMeters()).thenReturn(70.0);
        TestGeo.signIn(me);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Tỉnh uỷ quyền cho xã: chỉ cán bộ xã được giao (người đi) mô phỏng được, người giữ ca ở tỉnh chỉ theo dõi. */
    @Test
    void delegatedTrip_onlyTheDelegateSimulates() {
        UnitMember ward = new UnitMember();
        ward.setId(UUID.randomUUID());
        trip.setAssignedTo(ward);

        assertThat(service.getPlan("SOS-1", null).getSimulation().isAvailable()).isFalse();
        assertThatThrownBy(() -> service.startSimulation("SOS-1")).isInstanceOf(SosException.class)
                .extracting(e -> ((SosException) e).getHttpStatus()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.detourSimulation("SOS-1")).isInstanceOf(SosException.class);
        verify(simulation, never()).start(org.mockito.ArgumentMatchers.anyLong(), any(), any());
    }

    @Test
    void realIncident_allowed_noDemoSwitchNeeded() {
        service.startSimulation("SOS-1");
        verify(simulation).start(eq(11L), eq("594288"), any());
        assertThat(service.getPlan("SOS-1", null).getSimulation().isAvailable()).isTrue();
    }

    @Test
    @SuppressWarnings("unchecked")
    void drivesTheStraightLineFromTheStation_officersOnly() {

        service.startSimulation("SOS-1");

        ArgumentCaptor<List<RouteSimulation.Step>> steps = ArgumentCaptor.forClass(List.class);
        verify(simulation).start(eq(11L), eq("594288"), steps.capture());
        assertThat(steps.getValue()).hasSizeGreaterThan(5);
        assertThat(steps.getValue().get(0).lat()).isEqualTo(10.03); // starts at the station
        assertThat(service.getPlan("SOS-1", null).getSimulation().isAvailable()).isTrue();

        incident.setCitizenTokenHash(CitizenTokens.sha256("tok"));
        assertThat(service.getPlan("SOS-1", "tok").getSimulation()).isNull(); // the citizen never sees it

        service.stopSimulation("SOS-1");
        verify(simulation).stop(11L);
    }

    @Test
    void detour_turnsOffTheRoute_thenHeadsToTheScene() {
        List<double[]> path = List.of(new double[]{10.03, 105.76}, new double[]{10.031, 105.765}, new double[]{10.0276, 105.7574});
        when(routing.route(any())).thenReturn(new com.sosvietnam.service.RoutingService.RoadRoute("x", path, 900, 120));

        service.detourSimulation("SOS-1");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<double[]>> via = ArgumentCaptor.forClass(List.class);
        verify(routing).route(via.capture());
        assertThat(via.getValue()).hasSize(3); // vị trí xe -> điểm rẽ -> hiện trường
        assertThat(via.getValue().get(0)[0]).isEqualTo(10.03); // chưa có GPS: từ đầu tuyến (trạm)
        verify(simulation).start(eq(11L), eq("594288"), any());

        when(routing.route(any())).thenReturn(null);
        assertThatThrownBy(() -> service.detourSimulation("SOS-1")).isInstanceOf(SosException.class)
                .hasMessageContaining("đường rẽ");
    }

    @Test
    void needsAnEnRouteTripAndABadge() {
        goer.setBadgeNumber(" ");
        assertThatThrownBy(() -> service.startSimulation("SOS-1")).isInstanceOf(SosException.class)
                .hasMessageContaining("số hiệu");
        assertThat(service.getPlan("SOS-1", null).getSimulation().isAvailable()).isFalse();

        goer.setBadgeNumber("594288");
        trip.setStatus(DispatchStatus.ARRIVED);
        assertThatThrownBy(() -> service.startSimulation("SOS-1")).isInstanceOf(SosException.class)
                .hasMessageContaining("chưa trên đường");
    }

    @Test
    void polylineDecode_andEvenSteps() {
        // Google's documented example "_p~iF~ps|U_ulLnnqC_mqNvxq`@"
        List<double[]> pts = Polylines.decode("_p~iF~ps|U_ulLnnqC_mqNvxq`@");
        assertThat(pts).hasSize(3);
        assertThat(pts.get(0)).containsExactly(38.5, -120.2);
        assertThat(pts.get(2)[0]).isEqualTo(43.252);
        assertThat(Polylines.decode("")).isEmpty();

        // 1 km due north, 100 m steps, stop 30 m before: 0, 100 .. 900, 970 -> 11 steps heading north
        List<double[]> line = List.of(new double[]{10.0, 105.0}, new double[]{10.0 + 1000 / 111_195.0, 105.0});
        List<RouteSimulation.Step> steps = RouteSimulation.steps(line, 100, 30);
        assertThat(steps).hasSize(11);
        assertThat(RouteSimulation.distanceM(line.get(0), new double[]{steps.get(10).lat(), steps.get(10).lng()}))
                .isBetween(969.0, 971.0);
        assertThat(steps.get(3).heading()).isBetween(0.0, 0.5);
        assertThat(RouteSimulation.steps(List.of(line.get(0)), 100, 30)).isEmpty();
    }
}
