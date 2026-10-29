package com.sosvietnam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.*;
import com.sosvietnam.model.payload.enums.*;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.request.FieldPositionRequest;
import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.model.payload.response.FieldLinkResponse;
import com.sosvietnam.repository.*;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.security.FieldPrincipal;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.DispatchSimulationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.CallServiceImpl;
import com.sosvietnam.service.implement.DispatchServiceImpl;
import com.sosvietnam.service.implement.DispatchTrackingServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Link người phụ trách hiện trường (26/10/2026): cấp / cấp lại mã, trang link, GPS trình duyệt ưu tiên app, gọi 3 bên. */
class FieldLinkTest {

    final IncidentRepository incidents = mock(IncidentRepository.class);
    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final com.sosvietnam.service.SmsService sms = mock(com.sosvietnam.service.SmsService.class);
    final IncidentRouteRepository routes = mock(IncidentRouteRepository.class);
    final UnitMemberRepository members = mock(UnitMemberRepository.class);
    final DispatchTrackingServiceImpl tracking =
            new DispatchTrackingServiceImpl(dispatches, mock(com.sosvietnam.service.RouteRecalcService.class));
    final DispatchServiceImpl service = new DispatchServiceImpl(incidents, dispatches, routes,
            mock(RescueStationRepository.class), members, new com.sosvietnam.security.CurrentOfficer(),
            mock(IncidentNotifier.class), mock(com.sosvietnam.service.OfficerService.class),
            mock(IncidentClaimRepository.class), mock(IncidentReportRepository.class), tracking,
            mock(DispatchSimulationService.class), mock(com.sosvietnam.service.RoutingService.class), sms);
    final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    final CallServiceImpl calls = new CallServiceImpl(incidents, messaging, new ObjectMapper(), dispatches,
            mock(org.springframework.beans.factory.ObjectProvider.class));

    Account me;
    Incident incident;
    IncidentDispatch trip;
    UnitMember goer;

    @BeforeEach
    void setUp() {
        me = Account.builder().id(UUID.randomUUID()).email("me@sos.vn").firstName("An").lastName("Cán Bộ")
                .role(Role.builder().id(2L).roleName("OFFICER").build())
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(TestGeo.ward("Phường Tân An")).isActive(true).build();
        incident = Incident.builder().id("SOS-1").status(IncidentStatus.RESOLVING).handlerId(me.getId())
                .citizenName("Nguyễn Văn B").phone("0901234567").citizenTokenHash(CitizenTokens.sha256("tok"))
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD).emergencyType(EmergencyType.POLICE)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An"))
                .latitude(10.0276).longitude(105.7574).build();
        RescueStation station = RescueStation.builder().id(7L).name("Công An Phường Tân An").agencyType(AgencyType.POLICE)
                .level(StationLevel.WARD).latitude(10.03).longitude(105.76).build();
        goer = TestGeo.member(me);
        goer.setId(UUID.randomUUID());
        goer.setFullName("Trần Văn Đi");
        goer.setBadgeNumber("594288");
        trip = IncidentDispatch.builder().id(11L).incident(incident).station(station).dispatchedBy(TestGeo.member(me))
                .fieldMember(goer).role(DispatchRole.PRIMARY).status(DispatchStatus.EN_ROUTE)
                .dispatchedAt(java.time.LocalDateTime.now()).build();
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));
        when(dispatches.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc("SOS-1", DispatchRole.PRIMARY))
                .thenReturn(Optional.of(trip));
        when(dispatches.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dispatches.findByFieldTokenHash(any())).thenAnswer(i ->
                Optional.ofNullable(i.getArgument(0).equals(trip.getFieldTokenHash()) ? trip : null));
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.empty());
        TestGeo.signIn(me);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    /** Người phụ trách có số: link đi qua SMS (không dấu, 1 tin), cấp lại ngay sau đó bị chặn 60 giây. */
    @Test
    void issueLink_smsTheFieldOfficer_thenCooldown() {
        goer.setPhone("0912345678");
        when(sms.send(any(), any())).thenReturn(new com.sosvietnam.service.SmsService.Result(true, null));

        FieldLinkResponse r = service.issueFieldLink("SOS-1");
        org.mockito.ArgumentCaptor<String> text = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq("0912345678"), text.capture());
        assertThat(text.getValue()).contains("SOS-1").endsWith("/f/" + r.getToken())
                .matches("\\p{ASCII}+").hasSizeLessThanOrEqualTo(160);
        assertThat(r.getSms().isSent()).isTrue();
        assertThat(r.getSms().getPhoneMasked()).doesNotContain("2345");

        assertThatThrownBy(() -> service.issueFieldLink("SOS-1")).isInstanceOf(SosException.class)
                .extracting(e -> ((SosException) e).getHttpStatus())
                .isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
        assertThat(service.getFieldPlan(r.getToken()).getIncidentId()).isEqualTo("SOS-1"); // link cũ vẫn dùng được
    }

    /** Không có số / cổng SMS lỗi: vẫn cấp link (cán bộ sao chép), báo lý do, không chặn cấp lại. */
    @Test
    void noPhoneOrGatewayError_linkStillIssued_noCooldown() {
        FieldLinkResponse r = service.issueFieldLink("SOS-1");
        assertThat(r.getSms().isSent()).isFalse();
        assertThat(r.getSms().getError()).contains("chưa có số điện thoại");
        verifyNoInteractions(sms);

        goer.setPhone("0912345678");
        when(sms.send(any(), any())).thenReturn(new com.sosvietnam.service.SmsService.Result(false, "Cổng SMS lỗi (HTTP 500)"));
        assertThat(service.issueFieldLink("SOS-1").getSms().getError()).contains("HTTP 500");
        assertThat(service.issueFieldLink("SOS-1").getToken()).isNotBlank(); // gửi hỏng thì không tính lượt chặn
    }

    /** "Người nhận ca" (người giữ ca) có trên thẻ theo dõi; trang link / dân không thấy SĐT cá nhân của họ. */
    @Test
    void plan_hasTheReceiver_phoneOnlyForOfficers() {
        UnitMember holder = TestGeo.member(me);
        holder.setId(me.getId());
        holder.setFullName("Đinh Phương Ngân");
        holder.setPhone("0911222333");
        when(members.findById(me.getId())).thenReturn(Optional.of(holder));

        var officerView = service.getPlan("SOS-1", null).getReceiver();
        assertThat(officerView.getFullName()).contains("Đinh Phương Ngân");
        assertThat(officerView.getPhone()).isEqualTo("0911222333");

        String token = service.issueFieldLink("SOS-1").getToken();
        var fieldView = service.getFieldPlan(token).getPlan().getReceiver();
        assertThat(fieldView.getFullName()).contains("Đinh Phương Ngân");
        assertThat(fieldView.getPhone()).isNull();
    }

    @Test
    void holderIssuesLink_reissueVoidsTheOldOne() {
        FieldLinkResponse first = service.issueFieldLink("SOS-1");
        assertThat(first.getPath()).isEqualTo("/f/" + first.getToken());
        assertThat(first.getFieldMember().getFullName()).contains("Trần Văn Đi");
        var page = service.getFieldPlan(first.getToken());
        assertThat(page.getCitizenPhone()).isEqualTo("0901234567");
        assertThat(page.getPlan().getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(page.getPlan().getSimulation()).isNull(); // trang link không có nút mô phỏng

        FieldLinkResponse second = service.issueFieldLink("SOS-1");
        assertThatThrownBy(() -> service.getFieldPlan(first.getToken())).isInstanceOf(SosException.class)
                .hasMessageContaining("không còn hiệu lực");
        assertThat(service.getFieldPlan(second.getToken()).getIncidentId()).isEqualTo("SOS-1");
    }

    @Test
    void noFieldOfficerYet_orStranger_cannotIssue() {
        trip.setFieldMember(null);
        assertThatThrownBy(() -> service.issueFieldLink("SOS-1")).isInstanceOf(SosException.class)
                .hasMessageContaining("đã chọn người phụ trách");
        trip.setFieldMember(goer);
        trip.setStatus(DispatchStatus.EN_ROUTE);
        incident.setHandlerId(UUID.randomUUID());
        assertThatThrownBy(() -> service.issueFieldLink("SOS-1")).isInstanceOf(SosException.class)
                .hasMessageContaining("người giữ ca");
    }

    /** Link cấp được lúc chờ khởi hành; người đi bấm "Khởi hành" trên link; GPS chỉ nhận sau khi khởi hành. */
    @Test
    void fieldOfficer_departsFromTheLink_gpsOnlyAfterwards() {
        trip.setStatus(DispatchStatus.ACCEPTED);
        String token = service.issueFieldLink("SOS-1").getToken();
        FieldPositionRequest p = FieldPositionRequest.builder().latitude(10.03).longitude(105.76).build();
        assertThat(service.fieldPosition(token, p)).isFalse(); // chưa khởi hành: bỏ điểm

        assertThat(service.fieldDepart(token).getStatus()).isEqualTo(DispatchStatus.EN_ROUTE);
        assertThat(trip.getDepartedAt()).isNotNull();
        assertThat(service.fieldPosition(token, p)).isTrue();
        assertThatThrownBy(() -> service.fieldDepart(token)).isInstanceOf(SosException.class); // đã đi rồi
    }

    @Test
    void fieldOfficer_confirmsArrivalWithoutGps_withAReason() {
        String token = service.issueFieldLink("SOS-1").getToken();
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.of(
                com.sosvietnam.model.entity.IncidentRoute.builder().id(5L).dispatch(trip).destLat(10.03).destLng(105.76).build()));
        var plan = service.fieldArrive(token, "  Điện thoại mất GPS  ");
        assertThat(plan.getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        assertThat(trip.getArrivalSource()).isEqualTo(com.sosvietnam.model.payload.enums.ArrivalSource.FIELD_OVERRIDE);
        assertThat(trip.getArrivalNote()).isEqualTo("Điện thoại mất GPS");
    }

    @Test
    void closedCase_410_andArriveFromTheLink() {
        String token = service.issueFieldLink("SOS-1").getToken();
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.of(
                com.sosvietnam.model.entity.IncidentRoute.builder().id(5L).dispatch(trip).destLat(10.03).destLng(105.76).build()));
        assertThatThrownBy(() -> service.fieldArrive(token, null)).isInstanceOf(SosException.class)
                .hasMessageContaining("Chưa có tín hiệu GPS");
        assertThatThrownBy(() -> service.fieldArrive(token, "pin")).isInstanceOf(SosException.class)
                .hasMessageContaining("5 ký tự");
        service.fieldPosition(token, FieldPositionRequest.builder().latitude(10.0301).longitude(105.76).accuracyM(8.0).build());
        assertThat(service.fieldArrive(token, null).getStatus()).isEqualTo(DispatchStatus.ARRIVED);
        incident.setStatus(IncidentStatus.RESOLVED);
        assertThatThrownBy(() -> service.getFieldPlan(token)).isInstanceOf(SosException.class)
                .hasMessageContaining("đã kết thúc");
    }

    @Test
    void browserGps_isUsed_unlessTheAppSentAPointInTheLast20s() {
        String token = service.issueFieldLink("SOS-1").getToken();
        FieldPositionRequest p = FieldPositionRequest.builder().latitude(10.03).longitude(105.76).speedKmh(30.0).build();
        assertThat(service.fieldPosition(token, p)).isTrue();
        assertThat(tracking.lastPosition(11L).getSource()).isEqualTo("BROWSER");

        when(dispatches.findEnRouteByTrackerBadge("594288")).thenReturn(java.util.List.of(trip));
        tracking.accept(TraccarPositionRequest.builder()
                .device(TraccarPositionRequest.Device.builder().uniqueId("594288").build())
                .position(TraccarPositionRequest.Position.builder().latitude(10.031).longitude(105.761).build())
                .build());
        assertThat(service.fieldPosition(token, p)).isFalse(); // app vừa gửi: giữ điểm của app
        assertThat(tracking.lastPosition(11L).getSource()).isEqualTo("TRACCAR");
        assertThat(tracking.lastPosition(11L).getLatitude()).isEqualTo(10.031);
    }

    CallSignalMessage sentTo(String user) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(user), eq("/queue/calls"), payload.capture());
        return (CallSignalMessage) payload.getValue();
    }

    @Test
    void fieldCallsTheCitizen_andTheOfficerCallsTheField() {
        CallSignalMessage ring = CallSignalMessage.builder().incidentId("SOS-1").callId("c-1").action("ring").to("citizen").build();
        calls.relay(new FieldPrincipal("SOS-1", 11L), ring);
        CallSignalMessage out = sentTo("citizen:SOS-1");
        assertThat(out.getFrom()).isEqualTo("field");
        assertThat(out.getPeerName()).contains("Trần Văn Đi");

        reset(messaging);
        OfficerPrincipal officer = OfficerPrincipal.of(TestGeo.member(me));
        calls.relay(officer, ring.toBuilder().to("field").build());
        assertThat(sentTo("field:11").getFrom()).isEqualTo("officer");

        reset(messaging);
        calls.relay(new CitizenPrincipal("SOS-1"), ring.toBuilder().to("field").build());
        assertThat(sentTo("field:11").getFrom()).isEqualTo("citizen");
    }

    @Test
    void oldFieldSession_afterTheTripChanged_isBounced() {
        calls.relay(new FieldPrincipal("SOS-1", 99L),
                CallSignalMessage.builder().incidentId("SOS-1").callId("c-2").action("ring").build());
        CallSignalMessage out = sentTo("field:99");
        assertThat(out.getAction()).isEqualTo("reject");
        assertThat(out.getReason()).isEqualTo("closed");
    }
}
