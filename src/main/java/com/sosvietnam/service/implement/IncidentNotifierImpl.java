package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.response.IncidentEventMessage;
import com.sosvietnam.model.payload.response.IncidentQueueResponse;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.security.CitizenPrincipal;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentMediaRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.util.IncidentAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Phiên STOMP của trực ban mang OfficerPrincipal (gắn lúc CONNECT trong StompAuthChannelInterceptor),
 * nên chỉ cần duyệt các người đang kết nối, ai có quyền xem ca thì gửi bản riêng cho người đó.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentNotifierImpl implements IncidentNotifier {

    public static final String QUEUE_DESTINATION = "/queue/incidents";
    /** Người báo tin (CitizenPrincipal) nhận trạng thái ca của mình ở /user/queue/incident. */
    public static final String CITIZEN_DESTINATION = "/queue/incident";

    private final SimpMessagingTemplate messagingTemplate;
    private final SimpUserRegistry userRegistry;
    private final IncidentRepository incidentRepository;
    private final AccountRepository accountRepository;
    private final IncidentMediaRepository incidentMediaRepository;

    @Override
    public void notify(String incidentId, IncidentEvent event) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    send(incidentId, event);
                }
            });
        } else {
            send(incidentId, event);
        }
    }

    private void send(String incidentId, IncidentEvent event) {
        try {
            Incident incident = incidentRepository.findById(incidentId).orElse(null);
            if (incident == null) return;
            String handlerName = handlerNames(List.of(incident)).get(incident.getHandlerId());
            int mediaCount = (int) incidentMediaRepository.countByIncident_Id(incident.getId());
            for (SimpUser user : userRegistry.getUsers()) {
                if (!(user.getPrincipal() instanceof OfficerPrincipal viewer)) continue;
                if (!IncidentAccess.canView(viewer, incident)) continue;
                IncidentQueueResponse view = toView(incident, viewer, handlerName, mediaCount);
                messagingTemplate.convertAndSendToUser(viewer.getName(), QUEUE_DESTINATION,
                        new IncidentEventMessage(event, view));
            }
            // Màn chờ của người báo tin (gửi kể cả khi họ chưa kết nối: broker tự bỏ nếu không có ai nghe)
            messagingTemplate.convertAndSendToUser(CitizenPrincipal.nameOf(incident.getId()), CITIZEN_DESTINATION,
                    Map.of("event", event, "incident", IncidentTrackingResponse.from(incident, handlerName)));
        } catch (Exception e) {
            // Realtime chỉ là kênh báo nhanh: lỗi gửi không được làm hỏng thao tác chính
            log.warn("Could not push {} for incident {}: {}", event, incidentId, e.getMessage());
        }
    }

    @Override
    public Set<String> onlineEmails() {
        return userRegistry.getUsers().stream().map(SimpUser::getName).collect(Collectors.toSet());
    }

    @Override
    public List<IncidentQueueResponse> toQueueViews(Collection<Incident> incidents, OfficerPrincipal viewer) {
        Map<UUID, String> names = handlerNames(incidents);
        Map<String, Integer> media = mediaCounts(incidents);
        return incidents.stream()
                .map(i -> toView(i, viewer, names.get(i.getHandlerId()), media.getOrDefault(i.getId(), 0)))
                .toList();
    }

    private static IncidentQueueResponse toView(Incident incident, OfficerPrincipal viewer, String handlerName,
                                                int mediaCount) {
        IncidentQueueResponse.Handler handler = incident.getHandlerId() == null ? null
                : new IncidentQueueResponse.Handler(incident.getHandlerId(), handlerName);
        boolean heldByMe = incident.getHandlerId() != null && incident.getHandlerId().equals(viewer.accountId());
        return IncidentQueueResponse.from(incident, handler, heldByMe, IncidentAccess.canHandle(viewer, incident),
                mediaCount);
    }

    /** Số ảnh/video của nhiều ca trong 1 truy vấn (hàng đợi hiển thị "n tệp"). */
    private Map<String, Integer> mediaCounts(Collection<Incident> incidents) {
        Map<String, Integer> counts = new HashMap<>();
        if (incidents.isEmpty()) return counts;
        for (Object[] row : incidentMediaRepository.countByIncidentIds(incidents.stream().map(Incident::getId).toList())) {
            counts.put((String) row[0], ((Number) row[1]).intValue());
        }
        return counts;
    }

    private Map<UUID, String> handlerNames(Collection<Incident> incidents) {
        Set<UUID> ids = incidents.stream().map(Incident::getHandlerId).filter(Objects::nonNull).collect(Collectors.toSet());
        // HashMap (không dùng Map.of()): get(null) với ca chưa ai giữ phải trả null chứ không ném lỗi
        if (ids.isEmpty()) return new HashMap<>();
        return accountRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(Account::getId, Account::fullName, (a, b) -> a, HashMap::new));
    }
}
