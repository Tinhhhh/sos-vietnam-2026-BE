package com.sosvietnam;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.security.JwtTokenProvider;
import com.sosvietnam.security.OfficerPrincipalLoader;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.security.StompAuthChannelInterceptor;
import com.sosvietnam.util.CitizenTokens;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.security.Principal;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Kênh STOMP: không token thì không kết nối, không nghe được topic chung, chỉ gửi được tín hiệu gọi. */
class StompAuthChannelInterceptorTest {

    final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    final IncidentRepository incidents = mock(IncidentRepository.class);
    final StompAuthChannelInterceptor interceptor =
            new StompAuthChannelInterceptor(jwt, mock(OfficerPrincipalLoader.class), incidents,
            mock(com.sosvietnam.repository.IncidentDispatchRepository.class));
    final MessageChannel channel = mock(MessageChannel.class);
    final OfficerPrincipal officer = TestGeo.officer(UUID.randomUUID(), "o@sos.vn", "O", RoleName.OFFICER,
            null, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");

    static Message<byte[]> frame(StompCommand command, String destination, Map<String, String> headers, Principal user) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (destination != null) accessor.setDestination(destination);
        headers.forEach(accessor::setNativeHeader);
        if (user != null) accessor.setUser(user);
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    static Message<byte[]> frame(StompCommand command, String destination, String authorization, Principal user) {
        return frame(command, destination, authorization == null ? Map.of() : Map.of("Authorization", authorization), user);
    }

    @Test
    void connect_withoutOrWithBadToken_isRejected() {
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, (String) null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        when(jwt.validateToken(anyString())).thenReturn(false);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null, "Bearer abc.def.ghi", null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void citizenConnect_withTheIncidentToken_getsCitizenPrincipal() {
        Incident incident = Incident.builder().id("SOS-1").citizenTokenHash(CitizenTokens.sha256("good")).build();
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));

        Message<?> ok = interceptor.preSend(frame(StompCommand.CONNECT, null,
                Map.of("X-SOS-Incident-Id", "SOS-1", "X-SOS-Access-Token", "good"), null), channel);
        Principal user = StompHeaderAccessor.wrap(ok).getUser();
        assertThat(user).isInstanceOf(CitizenPrincipal.class);
        assertThat(user.getName()).isEqualTo("citizen:SOS-1");

        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null,
                Map.of("X-SOS-Incident-Id", "SOS-1", "X-SOS-Access-Token", "bad"), null), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.CONNECT, null,
                Map.of("X-SOS-Incident-Id", "SOS-404", "X-SOS-Access-Token", "good"), null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void subscribe_onlyToOwnUserQueue() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/incidents", (String) null, officer), channel))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/incidents", (String) null, officer), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/user/queue/incidents", (String) null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }

    @Test
    void send_onlyCallSignals_fromAConnectedUser() {
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/calls", (String) null, officer), channel))
                .doesNotThrowAnyException();
        assertThatCode(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/calls", (String) null,
                new CitizenPrincipal("SOS-1")), channel)).doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/x", (String) null, officer), channel))
                .isInstanceOf(MessageDeliveryException.class);
        assertThatThrownBy(() -> interceptor.preSend(frame(StompCommand.SEND, "/app/calls", (String) null, null), channel))
                .isInstanceOf(MessageDeliveryException.class);
    }
}
