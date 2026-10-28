package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.response.IceServersResponse;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.CallService;
import com.sosvietnam.util.CitizenTokens;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
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
 * 4 bên của một ca gọi được cho nhau: cán bộ giữ ca (officer), người báo tin (citizen), người phụ trách hiện trường
 * mở link (field) và cán bộ xã được uỷ quyền (delegate). Server không lưu SDP / ICE, chỉ chuyển tiếp.
 * Đổ chuông tới người không có phiên STOMP thì trả ngay reject "offline" (không để máy gọi chờ 45 giây vô ích).
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
    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;
    private final com.sosvietnam.repository.IncidentDispatchRepository incidentDispatchRepository;
    // ai đang có phiên STOMP (lấy muộn: bean do cấu hình WebSocket tạo); không có thì bỏ qua bước kiểm trực tuyến
    private final ObjectProvider<SimpUserRegistry> userRegistry;
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
    public IceServersResponse getIceServers(String incidentId, String citizenAccessToken, String fieldToken) {
        requireCaller(incidentId, citizenAccessToken, fieldToken);
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

    /** Cán bộ đã đăng nhập, người dân có đúng mã truy cập, hoặc người phụ trách hiện trường có link còn hiệu lực. */
    private void requireCaller(String incidentId, String citizenAccessToken, String fieldToken) {
        if (StringUtils.hasText(fieldToken)) {
            boolean ok = incidentDispatchRepository.findByFieldTokenHash(CitizenTokens.sha256(fieldToken.trim()))
                    .filter(d -> IncidentStatus.ACTIVE.contains(d.getIncident().getStatus())).isPresent();
            if (!ok) throw new SosException(HttpStatus.UNAUTHORIZED, "Link người phụ trách hiện trường không còn hiệu lực");
            return;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean loggedIn = auth != null && auth.isAuthenticated() && !(auth instanceof AnonymousAuthenticationToken);
        if (loggedIn) {
            if (auth.getPrincipal() instanceof OfficerPrincipal officer && officer.isStaff()) return;
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

        // bên gửi: vai trò + tên hiện trên máy bên nhận
        String from;
        String peerName;
        String unitName;
        String defaultTo;
        if (sender instanceof OfficerPrincipal officer) {
            if (officer.memberId().equals(incident.getHandlerId())) {
                from = ROLE_OFFICER;
            } else if (isDelegate(incident.getId(), officer.memberId())) {
                from = ROLE_DELEGATE;
            } else {
                bounce(sender, signal, "not_handler");
                return;
            }
            peerName = officer.fullName();
            unitName = unitName(officer);
            defaultTo = ROLE_CITIZEN;
        } else if (sender instanceof CitizenPrincipal citizen) {
            if (!citizen.incidentId().equals(incident.getId())) {
                return; // chỉ được gọi trong ca của chính mình
            }
            from = ROLE_CITIZEN;
            peerName = incident.getCitizenName();
            unitName = null;
            defaultTo = ROLE_OFFICER;
        } else if (sender instanceof com.sosvietnam.security.FieldPrincipal field) {
            com.sosvietnam.model.entity.IncidentDispatch trip = currentFieldTrip(incident.getId());
            if (!field.incidentId().equals(incident.getId()) || trip == null || !trip.getId().equals(field.dispatchId())) {
                bounce(sender, signal, "closed"); // link đã đổi người / cấp lại
                return;
            }
            from = ROLE_FIELD;
            peerName = trip.getFieldMember().displayName();
            unitName = trip.getStation() == null ? null : trip.getStation().getName();
            defaultTo = ROLE_OFFICER;
        } else {
            return;
        }

        String to = StringUtils.hasText(signal.getTo()) ? signal.getTo() : defaultTo;
        String target = switch (to) {
            // tên phiên STOMP của cán bộ = member id (OfficerPrincipal.getName)
            case ROLE_OFFICER -> incident.getHandlerId() == null ? null : incident.getHandlerId().toString();
            case ROLE_CITIZEN -> CitizenPrincipal.nameOf(incident.getId());
            case ROLE_FIELD -> {
                com.sosvietnam.model.entity.IncidentDispatch trip = currentFieldTrip(incident.getId());
                yield trip == null ? null : com.sosvietnam.security.FieldPrincipal.nameOf(trip.getId());
            }
            case ROLE_DELEGATE -> {
                com.sosvietnam.model.entity.IncidentDispatch trip = currentDelegateTrip(incident.getId());
                yield trip == null ? null : trip.getAssignedTo().getId().toString();
            }
            default -> null;
        };
        if (target == null || to.equals(from)) {
            bounce(sender, signal, switch (to) {
                case ROLE_FIELD -> "no_field";
                case ROLE_DELEGATE -> "no_delegate";
                default -> "no_handler";
            });
            return;
        }
        if ("ring".equals(action) && !online(target)) {
            bounce(sender, signal, "offline"); // vd người phụ trách chưa mở link, cán bộ đã tắt trang
            return;
        }
        // to = bên nhận thấy ai gọi tới nó; bên nhận trả lời về "from"
        send(target, signal.toBuilder().from(from).to(to).peerName(peerName).unitName(unitName).build());
        if ("accept".equals(action) || "reject".equals(action)) {
            // cùng người mở ở máy khác (laptop + điện thoại): các máy đó thôi đổ chuông
            send(sender.getName(), CallSignalMessage.builder().incidentId(signal.getIncidentId())
                    .callId(signal.getCallId()).action("dismiss").from("server").build());
        }
        if ("ring".equals(action) || "end".equals(action) || "reject".equals(action)) {
            log.info("📞 [CALL] {} {} on {} by {}", action, signal.getCallId(), incident.getId(), sender.getName());
        }
    }

    static final String ROLE_OFFICER = "officer";
    static final String ROLE_CITIZEN = "citizen";
    static final String ROLE_FIELD = "field";
    static final String ROLE_DELEGATE = "delegate";

    private boolean online(String user) {
        SimpUserRegistry registry = userRegistry == null ? null : userRegistry.getIfAvailable();
        return registry == null || registry.getUser(user) != null;
    }

    /** Lượt chủ trì đang uỷ quyền cho 1 cán bộ (chưa từ chối, chưa xong); null nếu không có. */
    private com.sosvietnam.model.entity.IncidentDispatch currentDelegateTrip(String incidentId) {
        return incidentDispatchRepository.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(
                        incidentId, com.sosvietnam.model.payload.enums.DispatchRole.PRIMARY)
                .filter(d -> d.getAssignedTo() != null
                        && com.sosvietnam.model.payload.enums.DispatchStatus.DELEGATED.contains(d.getStatus()))
                .orElse(null);
    }

    private boolean isDelegate(String incidentId, java.util.UUID memberId) {
        com.sosvietnam.model.entity.IncidentDispatch trip = currentDelegateTrip(incidentId);
        return trip != null && trip.getAssignedTo().getId().equals(memberId);
    }

    /** Lượt chủ trì hiện tại có người phụ trách hiện trường (đang đi / đã tới); null nếu chưa có. */
    private com.sosvietnam.model.entity.IncidentDispatch currentFieldTrip(String incidentId) {
        return incidentDispatchRepository.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(
                        incidentId, com.sosvietnam.model.payload.enums.DispatchRole.PRIMARY)
                .filter(d -> d.getFieldMember() != null
                        && (d.getStatus() == com.sosvietnam.model.payload.enums.DispatchStatus.ACCEPTED
                        || d.getStatus() == com.sosvietnam.model.payload.enums.DispatchStatus.EN_ROUTE
                        || d.getStatus() == com.sosvietnam.model.payload.enums.DispatchStatus.ARRIVED))
                .orElse(null);
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
        String area = officer.level() == UnitLevel.WARD ? officer.wardName() : officer.provinceName();
        return StringUtils.hasText(area) ? officer.agency().getLabel() + " " + area : officer.agency().getLabel();
    }
}
