package com.sosvietnam.config;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.ClaimEndReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * CHỈ ĐỂ TEST module điều động (bật bằng DEMO_INCIDENTS=true, mặc định tắt; test xong thì tắt hoặc xoá class này).
 *
 * Mỗi phút: mỗi loại ca test bên dưới luôn có 1 ca "[DEMO]" đang chờ nhận (PENDING, đúng cấp nhận ca) trong
 * hàng đợi; KHÔNG tự nhận ca, cán bộ tự bấm "Tiếp nhận". Ai nhận rồi thì phút sau có ca chờ mới.
 * Ca test đã điều động (RESOLVING) quá {@code app.demo.incidents.resolving-minutes} phút thì tự đóng (CANCELLED),
 * để cán bộ không bị kẹt ca (cán bộ không trả được ca RESOLVING).
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.demo.incidents.enabled", havingValue = "true")
public class DemoIncidentSeeder {

    public static final String MARK = "[DEMO] ";
    // Tên các ca test do script local-db/demo-incidents.mjs cũ tạo (không có MARK)
    private static final Set<String> LEGACY_NAMES =
            Set.of("Trần Văn Demo (tỉnh)", "Nguyễn Thị Demo (xã)", "Lê Văn Demo (CSGT)");

    /** @param level cấp nhận ca mong đợi (ca cấp xã quá 15' tự lên tỉnh thì không còn tính là ca chờ của xã) */
    private record DemoCase(UnitLevel level, String name, String phone, EmergencyType type, String tag,
                            String description, String address, double lat, double lng) {
    }

    private static final List<DemoCase> CASES = List.of(
            // Phường Tân An có công an xã -> vào hàng đợi xã
            new DemoCase(UnitLevel.WARD, "Nguyễn Thị Demo (xã)", "0901000001", EmergencyType.POLICE,
                    "Trộm cắp đột nhập", "Phát hiện người lạ đột nhập nhà, đã xác minh qua điện thoại.",
                    "02 Nguyễn Tri Phương", 10.0276, 105.7574),
            // Phường Cái Khế chưa có tài khoản công an cấp xã -> thẳng cấp tỉnh
            new DemoCase(UnitLevel.PROVINCE, "Trần Văn Demo (tỉnh)", "0901000002", EmergencyType.POLICE,
                    "Đánh nhau, gây rối", "Nhóm thanh niên đánh nhau trước quán, cần lực lượng hỗ trợ.",
                    "Đường Cách Mạng Tháng 8, Cái Khế", 10.0455, 105.7865),
            // Tai nạn giao thông -> CSGT cấp tỉnh
            new DemoCase(UnitLevel.PROVINCE, "Lê Văn Demo (CSGT)", "0901000003", EmergencyType.TRAFFIC,
                    "Va chạm giao thông", "Va chạm giữa xe máy và ô tô, có người bị thương nhẹ.",
                    "Bến Ninh Kiều, Hai Bà Trưng", 10.0341, 105.7880));

    private final IncidentRepository incidentRepository;
    private final IncidentClaimRepository incidentClaimRepository;
    private final IncidentService incidentService;

    @Value("${app.demo.incidents.resolving-minutes:30}")
    private long resolvingMinutes;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        log.warn("🧪 [DEMO INCIDENTS] bật: luôn có ca test chờ nhận cho công an xã / tỉnh / CSGT Cần Thơ (tắt bằng DEMO_INCIDENTS=false)");
        topUp();
    }

    @Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void topUp() {
        for (DemoCase demo : CASES) {
            try {
                ensureCase(demo);
            } catch (RuntimeException e) {
                log.warn("🧪 [DEMO INCIDENTS] {}: {}", demo.name(), e.getMessage());
            }
        }
    }

    private void ensureCase(DemoCase demo) {
        List<Incident> open = new java.util.ArrayList<>(incidentRepository.findByCitizenNameInAndStatusIn(
                List.of(MARK + demo.name(), demo.name()), IncidentStatus.ACTIVE));
        for (Incident stale : open.stream().filter(this::isStaleDemo).toList()) {
            closeDemo(stale);
            open.remove(stale);
        }
        // Đã có ca chờ nhận đúng cấp thì thôi (ca đang có người giữ không tính: cần ca mới để nhận tiếp)
        boolean waiting = open.stream().anyMatch(i -> i.getStatus() == IncidentStatus.PENDING
                && i.getHandlerId() == null && i.getRoutedLevel() == demo.level());
        if (waiting) return;

        CreateIncidentRequest request = new CreateIncidentRequest();
        request.setCitizenName(MARK + demo.name());
        // SĐT khác nhau mỗi ca (mỗi SĐT chỉ có 1 SOS đang mở); giữ 4 số đầu để dễ nhận ra ca demo
        request.setPhone(demo.phone().substring(0, 4) + String.format("%06d", java.util.concurrent.ThreadLocalRandom.current().nextInt(1_000_000)));
        request.setEmergencyType(demo.type());
        request.setIncidentTags(List.of(demo.tag()));
        request.setDescription(demo.description());
        request.setAddress(demo.address());
        request.setLatitude(demo.lat());
        request.setLongitude(demo.lng());
        request.setAccuracy(15);
        CreateIncidentResponse created = incidentService.createIncident(request);
        log.info("🧪 [DEMO INCIDENTS] ca test chờ nhận {} ({})", created.getIncident().getId(), demo.name());
    }

    private boolean isDemo(Incident incident) {
        String name = incident.getCitizenName();
        return name != null && (name.startsWith(MARK) || LEGACY_NAMES.contains(name));
    }

    private boolean isStaleDemo(Incident incident) {
        if (!isDemo(incident) || incident.getStatus() != IncidentStatus.RESOLVING) return false;
        LocalDateTime since = incident.getUpdatedAt() != null ? incident.getUpdatedAt() : incident.getCreatedAt();
        return since.isBefore(LocalDateTime.now().minusMinutes(resolvingMinutes));
    }

    /** Đóng ca test đã điều động quá lâu (không qua API vì cán bộ không trả được ca RESOLVING). */
    private void closeDemo(Incident incident) {
        java.util.UUID handlerId = incident.getHandlerId();
        LocalDateTime now = LocalDateTime.now();
        incident.setStatus(IncidentStatus.CANCELLED);
        incident.setCancelReason(CancelReason.MISTAKE);
        incident.setCancelledAt(now);
        incident.setHandlerId(null);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        log.info("🧪 [DEMO INCIDENTS] đóng ca test {} (RESOLVING quá {} phút)", incident.getId(), resolvingMinutes);
        if (handlerId == null) return;
        incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(incident.getId(), handlerId)
                .ifPresent(claim -> {
                    claim.setReleasedAt(now);
                    claim.setEndReason(ClaimEndReason.CLOSED);
                    incidentClaimRepository.save(claim);
                });
    }
}
