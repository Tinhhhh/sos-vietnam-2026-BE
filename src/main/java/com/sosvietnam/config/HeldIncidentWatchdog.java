package com.sosvietnam.config;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.IncidentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Ca VERIFYING mà cán bộ giữ ca mất kết nối (không còn phiên STOMP) quá `app.incident.offline-release-minutes`
 * thì tự trả về hàng đợi (incident_claims.end_reason = AUTO_RELEASED) để cán bộ khác nhận, người dân không phải chờ
 * mãi. Ca RESOLVING không nhả (lực lượng đang đi). Trạng thái trực tuyến lấy từ bộ nhớ của 1 instance BE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HeldIncidentWatchdog {

    private final IncidentRepository incidentRepository;
    private final IncidentNotifier notifier;
    private final IncidentService incidentService;

    /** Người giữ ca -> lúc bắt đầu thấy mất kết nối */
    private final Map<UUID, LocalDateTime> offlineSince = new ConcurrentHashMap<>();

    @Value("${app.incident.offline-release-minutes:3}")
    private long offlineReleaseMinutes;

    @Scheduled(initialDelay = 60_000, fixedDelay = 30_000)
    public void releaseOfflineHolders() {
        try {
            check(LocalDateTime.now());
        } catch (Exception e) {
            log.warn("⏏️ [AUTO-RELEASE] check failed: {}", e.getMessage());
        }
    }

    void check(LocalDateTime now) {
        List<Incident> held = incidentRepository.findByStatusAndHandlerIdIsNotNull(IncidentStatus.VERIFYING);
        Set<UUID> holders = held.stream().map(Incident::getHandlerId).collect(Collectors.toSet());
        offlineSince.keySet().retainAll(holders);
        if (held.isEmpty()) return;

        Set<UUID> online = notifier.onlineMemberIds();
        for (Incident incident : held) {
            UUID holder = incident.getHandlerId();
            if (online.contains(holder)) {
                offlineSince.remove(holder);
                continue;
            }
            LocalDateTime since = offlineSince.computeIfAbsent(holder, k -> now);
            if (since.isAfter(now.minusMinutes(offlineReleaseMinutes))) continue;
            if (incidentService.autoRelease(incident.getId(), holder,
                    "Tự trả về hàng đợi: cán bộ mất kết nối quá " + offlineReleaseMinutes + " phút")) {
                offlineSince.remove(holder);
            }
        }
    }
}
