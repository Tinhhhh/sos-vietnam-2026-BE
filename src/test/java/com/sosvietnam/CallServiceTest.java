package com.sosvietnam;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.response.IceServersResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.implement.CallServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Video call: chỉ người báo tin và cán bộ đang giữ ca nói chuyện được với nhau; ICE server khi chưa có TURN. */
class CallServiceTest {

    final IncidentRepository incidents = mock(IncidentRepository.class);
    final SimpMessagingTemplate messaging = mock(SimpMessagingTemplate.class);
    final CallServiceImpl service = new CallServiceImpl(incidents, messaging, new ObjectMapper());

    final UUID handlerId = UUID.randomUUID();
    final OfficerPrincipal handler = TestGeo.officer(handlerId, "congan.tanan@sos.vn", "Cán Bộ A",
            RoleName.OFFICER, AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");
    Incident incident;

    @BeforeEach
    void setUp() {
        incident = Incident.builder().id("SOS-1").status(IncidentStatus.VERIFYING).handlerId(handlerId)
                .citizenName("Nguyễn Văn B").citizenTokenHash(CitizenTokens.sha256("tok")).build();
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    CallSignalMessage signal(String action) {
        return CallSignalMessage.builder().incidentId("SOS-1").callId("c-1").action(action).build();
    }

    CallSignalMessage sentTo(String user) {
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSendToUser(eq(user), eq("/queue/calls"), payload.capture());
        return (CallSignalMessage) payload.getValue();
    }

    @Test
    void handlerRings_citizenGetsItWithOfficerAndUnitName() {
        service.relay(handler, signal("ring").toBuilder().from("citizen").peerName("spoofed").build());

        CallSignalMessage out = sentTo("citizen:SOS-1");
        assertThat(out.getAction()).isEqualTo("ring");
        assertThat(out.getFrom()).isEqualTo("officer"); // server overrides what the client claimed
        assertThat(out.getPeerName()).isEqualTo("Cán Bộ A");
        assertThat(out.getUnitName()).isEqualTo("Công An Phường Tân An");
    }

    @Test
    void citizenSignal_goesToTheHandlerOnly() {
        service.relay(new CitizenPrincipal("SOS-1"), signal("offer"));

        CallSignalMessage out = sentTo(handlerId.toString());
        assertThat(out.getFrom()).isEqualTo("citizen");
        assertThat(out.getPeerName()).isEqualTo("Nguyễn Văn B");
    }

    @Test
    void otherOfficer_isRejected_notForwarded() {
        OfficerPrincipal other = TestGeo.officer(UUID.randomUUID(), "other@sos.vn", "Khác", RoleName.OFFICER,
                AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");
        service.relay(other, signal("ring"));

        CallSignalMessage out = sentTo(other.getName()); // phiên STOMP của cán bộ = member id
        assertThat(out.getAction()).isEqualTo("reject");
        assertThat(out.getReason()).isEqualTo("not_handler");
        verify(messaging, never()).convertAndSendToUser(eq("citizen:SOS-1"), anyString(), any());
    }

    @Test
    void citizenOfAnotherIncident_isIgnored() {
        service.relay(new CitizenPrincipal("SOS-2"), signal("ring"));
        verifyNoInteractions(messaging);
    }

    @Test
    void citizenCallsBack_beforeAnyoneTookTheCase_getsRejected() {
        incident.setHandlerId(null);
        service.relay(new CitizenPrincipal("SOS-1"), signal("ring"));

        assertThat(sentTo("citizen:SOS-1").getReason()).isEqualTo("no_handler");
    }

    @Test
    void closedIncident_endsTheCall_andJunkIsDropped() {
        incident.setStatus(IncidentStatus.CANCELLED);
        service.relay(handler, signal("offer"));
        CallSignalMessage out = sentTo(handlerId.toString());
        assertThat(out.getAction()).isEqualTo("end");
        assertThat(out.getReason()).isEqualTo("closed");

        reset(messaging);
        service.relay(handler, signal("hack"));
        service.relay(handler, signal("ring").toBuilder().callId(null).build());
        verifyNoInteractions(messaging);
    }

    @Test
    void iceServers_withoutTurnConfig_areStunOnly_forCitizenWithToken() {
        IceServersResponse res = service.getIceServers("SOS-1", "tok");
        assertThat(res.isRelay()).isFalse();
        assertThat(res.getIceServers()).hasSize(1);
        assertThat(res.getIceServers().get(0).getUrls()).allMatch(u -> u.startsWith("stun:"));

        assertThatThrownBy(() -> service.getIceServers("SOS-1", "wrong"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
        assertThatThrownBy(() -> service.getIceServers(null, null))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cloudflareResponse_isParsed_andPort53UrlsDropped() throws Exception {
        String body = """
                {"iceServers":[
                  {"urls":["stun:stun.cloudflare.com:3478","stun:stun.cloudflare.com:53"]},
                  {"urls":["turn:turn.cloudflare.com:3478?transport=udp","turn:turn.cloudflare.com:53?transport=udp",
                           "turns:turn.cloudflare.com:443?transport=tcp"],
                   "username":"u","credential":"c"}]}""";
        Method parse = CallServiceImpl.class.getDeclaredMethod("parseCloudflare", com.fasterxml.jackson.databind.JsonNode.class);
        parse.setAccessible(true);
        List<IceServersResponse.IceServer> servers =
                (List<IceServersResponse.IceServer>) parse.invoke(null, new ObjectMapper().readTree(body));

        assertThat(servers).hasSize(2);
        assertThat(servers.get(0).getUrls()).containsExactly("stun:stun.cloudflare.com:3478");
        assertThat(servers.get(1).getUrls()).containsExactly(
                "turn:turn.cloudflare.com:3478?transport=udp", "turns:turn.cloudflare.com:443?transport=tcp");
        assertThat(servers.get(1).getUsername()).isEqualTo("u");
    }
}
