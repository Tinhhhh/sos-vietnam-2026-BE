package com.sosvietnam.service.implement;

import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.response.IncidentEventMessage;
import com.sosvietnam.model.payload.response.IncidentQueueResponse;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.security.CitizenPrincipal;
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
    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final IncidentMediaRepository incidentMediaRepository;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final com.sosvietnam.repository.IncidentDocumentRepository incidentDocumentRepository;
    private final com.sosvietnam.service.DemoModeService demoModeService;

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
            IncidentQueueResponse.MediaCounts mediaCount = mediaCounts(List.of(incident))
                    .getOrDefault(incident.getId(), IncidentQueueResponse.MediaCounts.NONE);
            IncidentDispatch dispatch = incidentDispatchRepository
                    .findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(incidentId, DispatchRole.PRIMARY).orElse(null);
            // Công tắc "ẩn ca demo": cán bộ chỉ nhận lệnh gỡ thẻ (người báo tin vẫn nhận bình thường)
            IncidentEvent officerEvent = demoModeService.hidden() && com.sosvietnam.util.DemoIncidents.isDemo(incident)
                    ? IncidentEvent.INCIDENT_REMOVED : event;
            for (SimpUser user : userRegistry.getUsers()) {
                if (!(user.getPrincipal() instanceof OfficerPrincipal viewer)) continue;
                IncidentQueueResponse view;
                if (IncidentAccess.canView(viewer, incident) || IncidentAccess.isDelegatedUnit(viewer, dispatch)) {
                    view = toView(incident, viewer, handlerName, mediaCount, dispatch);
                    messagingTemplate.convertAndSendToUser(viewer.getName(), QUEUE_DESTINATION,
                            new IncidentEventMessage(officerEvent, view));
                } else if (IncidentAccess.isStationUnit(viewer, dispatch)) {
                    // Đơn vị vừa từ chối uỷ quyền: gỡ thẻ ca khỏi hàng đợi của họ
                    view = toView(incident, viewer, handlerName, mediaCount, dispatch);
                    messagingTemplate.convertAndSendToUser(viewer.getName(), QUEUE_DESTINATION,
                            new IncidentEventMessage(IncidentEvent.INCIDENT_REMOVED, view));
                }
            }
            // Màn chờ của người báo tin (gửi kể cả khi họ chưa kết nối: broker tự bỏ nếu không có ai nghe)
            sendCitizen(incident, event, handlerName);
        } catch (Exception e) {
            // Realtime chỉ là kênh báo nhanh: lỗi gửi không được làm hỏng thao tác chính
            log.warn("Could not push {} for incident {}: {}", event, incidentId, e.getMessage());
        }
    }

    @Override
    public void notifyRemovedFor(String incidentId, com.sosvietnam.model.payload.enums.AgencyType previousAgency) {
        Runnable push = () -> {
            try {
                Incident incident = incidentRepository.findById(incidentId).orElse(null);
                if (incident == null) return;
                String handlerName = handlerNames(List.of(incident)).get(incident.getHandlerId());
                IncidentQueueResponse.MediaCounts mediaCount = mediaCounts(List.of(incident))
                        .getOrDefault(incident.getId(), IncidentQueueResponse.MediaCounts.NONE);
                for (SimpUser user : userRegistry.getUsers()) {
                    if (!(user.getPrincipal() instanceof OfficerPrincipal viewer)) continue;
                    if (viewer.agency() != previousAgency || IncidentAccess.canView(viewer, incident)) continue;
                    messagingTemplate.convertAndSendToUser(viewer.getName(), QUEUE_DESTINATION, new IncidentEventMessage(
                            IncidentEvent.INCIDENT_REMOVED, toView(incident, viewer, handlerName, mediaCount, null)));
                }
            } catch (Exception e) {
                log.warn("Could not push removal of incident {}: {}", incidentId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    @Override
    public void notifyCitizen(String incidentId) {
        Runnable push = () -> {
            try {
                Incident incident = incidentRepository.findById(incidentId).orElse(null);
                if (incident == null) return;
                sendCitizen(incident, IncidentEvent.INCIDENT_UPDATED,
                        handlerNames(List.of(incident)).get(incident.getHandlerId()));
            } catch (Exception e) {
                log.warn("Could not push the citizen update for incident {}: {}", incidentId, e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    push.run();
                }
            });
        } else {
            push.run();
        }
    }

    private void sendCitizen(Incident incident, IncidentEvent event, String handlerName) {
        String documentStatus = com.sosvietnam.model.entity.IncidentDocument.statusOf(
                incidentDocumentRepository.findById(incident.getId()).orElse(null));
        messagingTemplate.convertAndSendToUser(CitizenPrincipal.nameOf(incident.getId()), CITIZEN_DESTINATION,
                Map.of("event", event, "incident", IncidentTrackingResponse.from(incident, handlerName, documentStatus)));
    }

    @Override
    public List<OfficerPrincipal> onlineOfficers() {
        return userRegistry.getUsers().stream()
                .map(SimpUser::getPrincipal)
                .filter(OfficerPrincipal.class::isInstance)
                .map(OfficerPrincipal.class::cast)
                .toList();
    }

    @Override
    public Set<UUID> onlineMemberIds() {
        return onlineOfficers().stream().map(OfficerPrincipal::memberId).collect(Collectors.toSet());
    }

    @Override
    public List<IncidentQueueResponse> toQueueViews(Collection<Incident> incidents, OfficerPrincipal viewer) {
        Map<UUID, String> names = handlerNames(incidents);
        Map<String, IncidentQueueResponse.MediaCounts> media = mediaCounts(incidents);
        Map<String, IncidentDispatch> dispatches = incidents.isEmpty() ? Map.of()
                : incidentDispatchRepository.findByIncident_IdInAndRole(
                                incidents.stream().map(Incident::getId).toList(), DispatchRole.PRIMARY).stream()
                        // mới nhất của mỗi ca
                        .collect(Collectors.toMap(d -> d.getIncident().getId(), d -> d,
                                (a, b) -> a.getDispatchedAt().isAfter(b.getDispatchedAt()) ? a : b));
        return incidents.stream()
                .map(i -> toView(i, viewer, names.get(i.getHandlerId()),
                        media.getOrDefault(i.getId(), IncidentQueueResponse.MediaCounts.NONE), dispatches.get(i.getId())))
                .toList();
    }

    private static IncidentQueueResponse toView(Incident incident, OfficerPrincipal viewer, String handlerName,
                                                IncidentQueueResponse.MediaCounts mediaCount, IncidentDispatch dispatch) {
        IncidentQueueResponse.Handler handler = incident.getHandlerId() == null ? null
                : new IncidentQueueResponse.Handler(incident.getHandlerId(), handlerName);
        boolean heldByMe = incident.getHandlerId() != null && incident.getHandlerId().equals(viewer.memberId());
        IncidentQueueResponse view = IncidentQueueResponse.from(incident, handler, heldByMe,
                IncidentAccess.canHandle(viewer, incident), mediaCount);
        view.setDelegation(delegationOf(dispatch, viewer));
        view.setDispatchStep(dispatch == null || dispatch.getStatus() == com.sosvietnam.model.payload.enums.DispatchStatus.REJECTED
                ? null : dispatch.getStatus().getStep());
        return view;
    }

    /** Khối "uỷ quyền" của thẻ ca: chỉ khi trạm chủ trì là đơn vị khác đã được giao cho một cán bộ. */
    private static IncidentQueueResponse.Delegation delegationOf(IncidentDispatch d, OfficerPrincipal viewer) {
        if (d == null || d.getAssignedTo() == null) return null;
        com.sosvietnam.model.entity.UnitMember byMember = d.getDispatchedBy();
        Account by = byMember == null ? null : byMember.getAccount();
        return IncidentQueueResponse.Delegation.builder()
                .dispatchId(d.getId())
                .status(d.getStatus())
                .statusLabel(d.statusLabel())
                .step(d.getStatus().getStep())
                .stationName(d.getStation().getName())
                .fromUnit(by == null || by.getAgencyType() == null ? null
                        : by.getAgencyType().getLabel() + (by.getUnitLevel() == null ? ""
                        : " · " + by.getAgencyType().levelLabel(by.getUnitLevel())))
                .fromOfficer(byMember == null ? null : byMember.displayName())
                .assignedTo(new IncidentQueueResponse.Handler(d.getAssignedTo().getId(), d.getAssignedTo().displayName()))
                .assignedToMe(IncidentAccess.isAssignee(viewer, d))
                .delegatedToMyUnit(IncidentAccess.isDelegatedUnit(viewer, d))
                .rejectReason(d.getRejectReason())
                .build();
    }

    /** Số ảnh / video của nhiều ca trong 1 truy vấn (thẻ ca hiện biểu tượng ảnh, máy quay + số lượng). */
    private Map<String, IncidentQueueResponse.MediaCounts> mediaCounts(Collection<Incident> incidents) {
        Map<String, int[]> raw = new HashMap<>(); // [total, images, videos]
        if (incidents.isEmpty()) return new HashMap<>();
        for (Object[] row : incidentMediaRepository.countByIncidentIdsAndKind(
                incidents.stream().map(Incident::getId).toList())) {
            int[] c = raw.computeIfAbsent((String) row[0], k -> new int[3]);
            int n = ((Number) row[2]).intValue();
            c[0] += n;
            if (row[1] == com.sosvietnam.model.payload.enums.MediaKind.IMAGE) c[1] += n;
            if (row[1] == com.sosvietnam.model.payload.enums.MediaKind.VIDEO) c[2] += n;
        }
        Map<String, IncidentQueueResponse.MediaCounts> counts = new HashMap<>();
        raw.forEach((id, c) -> counts.put(id, new IncidentQueueResponse.MediaCounts(c[0], c[1], c[2])));
        return counts;
    }

    private Map<UUID, String> handlerNames(Collection<Incident> incidents) {
        Set<UUID> ids = incidents.stream().map(Incident::getHandlerId).filter(Objects::nonNull).collect(Collectors.toSet());
        // HashMap (không dùng Map.of()): get(null) với ca chưa ai giữ phải trả null chứ không ném lỗi
        if (ids.isEmpty()) return new HashMap<>();
        return unitMemberRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(com.sosvietnam.model.entity.UnitMember::getId,
                        com.sosvietnam.model.entity.UnitMember::displayName, (a, b) -> a, HashMap::new));
    }
}
