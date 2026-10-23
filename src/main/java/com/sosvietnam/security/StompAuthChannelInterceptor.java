package com.sosvietnam.security;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.util.CitizenTokens;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Bảo vệ kênh STOMP (/ws, /ws-raw):
 * - CONNECT của cán bộ: header "Authorization: Bearer <access token>" (OFFICER/ADMIN);
 *   trình duyệt không gắn được header vào bước bắt tay WebSocket nên token đi trong frame CONNECT.
 *   Phiên được gắn OfficerPrincipal (email + lực lượng + cấp + địa bàn) để gửi tin theo người.
 * - CONNECT của người báo tin: header "X-SOS-Incident-Id" + "X-SOS-Access-Token" (mã trả về lúc gửi SOS);
 *   phiên gắn CitizenPrincipal, chỉ nhận tin của đúng ca đó.
 * - Chỉ được SUBSCRIBE kênh riêng /user/queue/... ; không nghe được topic chung của người khác.
 * - Chỉ được SEND tới /app/calls (tín hiệu video call); còn lại server chỉ đẩy tin xuống.
 */
@Component
@RequiredArgsConstructor
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    public static final String CITIZEN_INCIDENT_HEADER = "X-SOS-Incident-Id";
    public static final String CITIZEN_TOKEN_HEADER = "X-SOS-Access-Token";
    public static final String CALLS_SEND_DESTINATION = "/app/calls";

    private final JwtTokenProvider jwtTokenProvider;
    private final AccountRepository accountRepository;
    private final IncidentRepository incidentRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }
        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String incidentId = accessor.getFirstNativeHeader(CITIZEN_INCIDENT_HEADER);
            accessor.setUser(StringUtils.hasText(incidentId)
                    ? authenticateCitizen(incidentId, accessor.getFirstNativeHeader(CITIZEN_TOKEN_HEADER))
                    : authenticate(accessor.getFirstNativeHeader("Authorization")));
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (accessor.getUser() == null || destination == null || !destination.startsWith("/user/queue/")) {
                throw new MessageDeliveryException("Không được nghe kênh này");
            }
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            if (accessor.getUser() == null || !CALLS_SEND_DESTINATION.equals(accessor.getDestination())) {
                throw new MessageDeliveryException("Kênh chỉ nhận tin từ máy chủ");
            }
        }
        return message;
    }

    private CitizenPrincipal authenticateCitizen(String incidentId, String token) {
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (!CitizenTokens.matches(incident, token)) {
            throw new MessageDeliveryException("Mã truy cập của người báo tin không hợp lệ");
        }
        return new CitizenPrincipal(incident.getId());
    }

    private OfficerPrincipal authenticate(String header) {
        if (!StringUtils.hasText(header) || !header.startsWith("Bearer ")) {
            throw new MessageDeliveryException("Thiếu token đăng nhập");
        }
        String jwt = header.substring(7);
        if (!jwtTokenProvider.validateToken(jwt) || jwtTokenProvider.isRefreshToken(jwt)
                || jwtTokenProvider.isTokenExpired(jwt)) {
            throw new MessageDeliveryException("Token không hợp lệ hoặc đã hết hạn");
        }
        AccessToken token = jwtTokenProvider.getTokenFromJwt(jwt);
        if (token == null || jwtTokenProvider.isTokenRevoked(token.getUserId(), token.getJwtId())
                || jwtTokenProvider.isIssuedBeforeLogoutAll(jwt)) {
            throw new MessageDeliveryException("Token đã bị thu hồi");
        }
        OfficerPrincipal officer = accountRepository.findByEmail(jwtTokenProvider.getUsernameFromJwt(jwt))
                .filter(a -> a.isActive() && !a.isLocked())
                .map(OfficerPrincipal::of)
                .orElseThrow(() -> new MessageDeliveryException("Tài khoản không hoạt động"));
        if (!officer.isStaff()) {
            throw new MessageDeliveryException("Chỉ cán bộ mới được nhận tin sự cố");
        }
        return officer;
    }
}
