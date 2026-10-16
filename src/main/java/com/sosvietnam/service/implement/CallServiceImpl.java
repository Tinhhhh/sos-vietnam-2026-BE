package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.response.IceServersResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.CallService;
import com.sosvietnam.util.CitizenTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Cuộc gọi video: cấp máy chủ ICE (STUN + TURN Cloudflare) và chuyển tiếp tín hiệu WebRTC qua STOMP.
 * Chỉ 2 bên của một ca được nói chuyện với nhau: người báo tin (CitizenPrincipal) và cán bộ đang giữ ca.
 * Server không lưu SDP / ICE, chỉ chuyển tiếp.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CallServiceImpl implements CallService {

    public static final String CALLS_DESTINATION = "/queue/calls";

    private static final Set<String> ACTIONS =
            Set.of("ring", "accept", "reject", "offer", "answer", "ice", "quality", "end");
    private static final List<IceServersResponse.IceServer> STUN_ONLY = List.of(IceServersResponse.IceServer.builder()
            .urls(List.of("stun:stun.cloudflare.com:3478", "stun:stun.l.google.com:19302")).build());
    private static final String CLOUDFLARE_URL =
            "https://rtc.live.cloudflare.com/v1/turn/keys/%s/credentials/generate-ice-servers";

    private final IncidentRepository incidentRepository;
    private final AccountRepository accountRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    // Tạo lúc gọi Cloudflare lần đầu: không cấu hình TURN thì không mở kết nối nào
    private volatile HttpClient httpClient;

    @Value("${app.turn.cloudflare.key-id:}")
    private String turnKeyId;
    @Value("${app.turn.cloudflare.api-token:}")
    private String turnApiToken;
    // Thời hạn mật khẩu TURN Cloudflare cấp; cache lại tới gần hết hạn để không gọi Cloudflare mỗi cuộc gọi
    @Value("${app.turn.ttl-seconds:3600}")
    private long turnTtlSeconds;

    private volatile CachedIce cached;

    private record CachedIce(IceServersResponse response, Instant expiresAt) {
    }

    // ------------------------------------------------------------------ ICE servers

    @Override
    public IceServersResponse getIceServers(String incidentId, String citizenAccessToken) {
        requireCaller(incidentId, citizenAccessToken);
        if (!StringUtils.hasText(turnKeyId) || !StringUtils.hasText(turnApiToken)) {
            return IceServersResponse.builder().iceServers(STUN_ONLY).ttlSeconds(turnTtlSeconds).relay(false).build();
        }
        CachedIce current = cached;
        if (current != null && current.expiresAt().isAfter(Instant.now())) {
            return current.response();
        }
        try {
            IceServersResponse fresh = fetchCloudflare();
            // Trả cho trình duyệt ttl còn lại thực tế; làm mới trước khi hết hạn 5 phút
            cached = new CachedIce(fresh, Instant.now().plusSeconds(Math.max(60, turnTtlSeconds - 300)));
            return fresh;
        } catch (Exception e) {
            // Cloudflare lỗi: vẫn gọi được bằng STUN (wifi thường đủ), không làm hỏng cuộc gọi
            log.warn("☁️ [TURN] Cloudflare TURN unavailable, STUN only: {}", e.getMessage());
            return IceServersResponse.builder().iceServers(STUN_ONLY).ttlSeconds(300).relay(false).build();
        }
    }

    private IceServersResponse fetchCloudflare() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(CLOUDFLARE_URL.formatted(turnKeyId)))
                .timeout(Duration.ofSeconds(8))
                .header("Authorization", "Bearer " + turnApiToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(Map.of("ttl", turnTtlSeconds))))
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }
        List<IceServersResponse.IceServer> servers = parseCloudflare(objectMapper.readTree(response.body()));
        if (servers.isEmpty()) throw new IllegalStateException("empty iceServers");
        boolean relay = servers.stream().anyMatch(s -> s.getUsername() != null);
        return IceServersResponse.builder().iceServers(servers).ttlSeconds(turnTtlSeconds).relay(relay).build();
    }

    private HttpClient httpClient() {
        if (httpClient == null) {
            synchronized (this) {
                if (httpClient == null) {
                    httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
                }
            }
        }
        return httpClient;
    }

    /**
     * Cloudflare trả { iceServers: [ {urls,...}, ... ] } (bản cũ: 1 object). Bỏ URL cổng 53:
     * nhiều trình duyệt chặn cổng DNS nên mỗi URL đó chỉ làm chậm bước dò đường.
     */
    static List<IceServersResponse.IceServer> parseCloudflare(JsonNode body) {
        JsonNode node = body.path("iceServers");
        List<JsonNode> entries = new ArrayList<>();
        if (node.isArray()) node.forEach(entries::add);
        else if (node.isObject()) entries.add(node);
        List<IceServersResponse.IceServer> servers = new ArrayList<>();
        for (JsonNode entry : entries) {
            List<String> urls = new ArrayList<>();
            JsonNode u = entry.path("urls");
            if (u.isArray()) u.forEach(x -> urls.add(x.asText()));
            else if (u.isTextual()) urls.add(u.asText());
            urls.removeIf(url -> url.matches(".*:53(\\?.*)?$"));
            if (urls.isEmpty()) continue;
            servers.add(IceServersResponse.IceServer.builder()
                    .urls(urls)
                    .username(entry.hasNonNull("username") ? entry.get("username").asText() : null)
                    .credential(entry.hasNonNull("credential") ? entry.get("credential").asText() : null)
                    .build());
        }
        return servers;
    }

    /** Cán bộ đã đăng nhập, hoặc người dân có đúng mã truy cập của một ca còn mở. */
    private void requireCaller(String incidentId, String citizenAccessToken) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (loggedIn) {
            boolean staff = accountRepository.findByEmail(auth.getName())
                    .map(OfficerPrincipal::of).map(OfficerPrincipal::isStaff).orElse(false);
            if (staff) return;
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ hoặc người báo tin mới gọi được");
        }
        if (!StringUtils.hasText(incidentId) || !StringUtils.hasText(citizenAccessToken)) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Thiếu mã truy cập của người báo tin");
        }
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy vụ việc ID: " + incidentId));
        if (!CitizenTokens.matches(incident, citizenAccessToken)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
        }
    }

    // ------------------------------------------------------------------ signaling

    @Override
    public void relay(Principal sender, CallSignalMessage signal) {
        if (sender == null || signal == null) return;
        String action = signal.getAction();
        if (!ACTIONS.contains(action) || !StringUtils.hasText(signal.getCallId()) || signal.getCallId().length() > 64
                || !StringUtils.hasText(signal.getIncidentId())) {
            return; // tín hiệu rác: bỏ qua
        }
        Incident incident = incidentRepository.findById(signal.getIncidentId()).orElse(null);
        if (incident == null || !IncidentStatus.ACTIVE.contains(incident.getStatus())) {
            bounce(sender, signal, "closed");
            return;
        }

        if (sender instanceof OfficerPrincipal officer) {
            if (!officer.accountId().equals(incident.getHandlerId())) {
                bounce(sender, signal, "not_handler");
                return;
            }
            send(CitizenPrincipal.nameOf(incident.getId()), signal.toBuilder()
                    .from("officer").peerName(officer.fullName()).unitName(unitName(officer)).build());
        } else if (sender instanceof CitizenPrincipal citizen) {
            if (!citizen.incidentId().equals(incident.getId())) {
                return; // chỉ được gọi trong ca của chính mình
            }
            Account handler = incident.getHandlerId() == null ? null
                    : accountRepository.findById(incident.getHandlerId()).orElse(null);
            if (handler == null) {
                bounce(sender, signal, "no_handler");
                return;
            }
            send(handler.getEmail(), signal.toBuilder()
                    .from("citizen").peerName(incident.getCitizenName()).unitName(null).build());
        }
        if ("ring".equals(action) || "end".equals(action) || "reject".equals(action)) {
            log.info("📞 [CALL] {} {} on {} by {}", action, signal.getCallId(), incident.getId(), sender.getName());
        }
    }

    private void send(String user, CallSignalMessage signal) {
        messagingTemplate.convertAndSendToUser(user, CALLS_DESTINATION, signal);
    }

    /** Báo lại người gửi để máy họ thôi đổ chuông / đóng cuộc gọi. Không trả lời một reject/end (tránh vòng lặp). */
    private void bounce(Principal sender, CallSignalMessage signal, String reason) {
        if ("reject".equals(signal.getAction()) || "end".equals(signal.getAction())) return;
        send(sender.getName(), CallSignalMessage.builder()
                .incidentId(signal.getIncidentId()).callId(signal.getCallId())
                .action("ring".equals(signal.getAction()) ? "reject" : "end")
                .reason(reason).from("server").build());
    }

    /** "Công An Phường Tân An", "Cảnh Sát PCCC & CNCH Cần Thơ"... */
    private static String unitName(OfficerPrincipal officer) {
        if (officer.agency() == null) return null;
        String area = officer.level() == UnitLevel.WARD ? officer.ward() : officer.province();
        return StringUtils.hasText(area) ? officer.agency().getLabel() + " " + area : officer.agency().getLabel();
    }
}
