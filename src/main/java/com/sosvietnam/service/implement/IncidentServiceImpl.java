package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.repository.IncidentReportRepository;
import com.sosvietnam.repository.IncidentRouteRepository;
import com.sosvietnam.model.entity.IncidentMedia;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.ClaimEndReason;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.MediaKind;
import com.sosvietnam.model.payload.enums.UploaderType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.DispatchRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.ReleaseIncidentRequest;
import com.sosvietnam.model.payload.response.CreateIncidentResponse;
import com.sosvietnam.model.payload.response.HandoverCandidateResponse;
import com.sosvietnam.model.payload.response.IncidentDetailResponse;
import com.sosvietnam.model.payload.response.IncidentHistoryResponse;
import com.sosvietnam.model.payload.response.IncidentLookupResponse;
import com.sosvietnam.model.payload.response.IncidentQueueResponse;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.IncidentResponse;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentMediaRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.IncidentService;
import com.sosvietnam.service.MediaAccess;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.util.AreaNames;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.DemoIncidents;
import com.sosvietnam.util.LookupCodes;
import com.sosvietnam.util.IncidentAccess;
import com.sosvietnam.util.MediaKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Nghiệp vụ tin báo SOS: tạo + phân luồng sự cố, hàng đợi theo đơn vị, nhận / nhả / giao ca,
 * phân công trạm, đính kèm / tải ảnh-video hiện trường.
 * Mỗi thay đổi được gửi realtime (IncidentNotifier) tới đúng các trực ban có quyền xem.
 * Người báo tin không có tài khoản: họ chứng minh quyền với sự cố bằng citizen access token
 * (chỉ trả về 1 lần khi tạo, DB chỉ lưu SHA-256 của token).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentServiceImpl implements IncidentService {

    private static final int MAX_MEDIA_PER_INCIDENT = 10;
    private static final int MAX_QUEUE_PAGE_SIZE = 100;
    // Bộ lọc thời gian của hàng đợi / lịch sử (FE dùng 1 / 7 / 30 ngày)
    private static final int MAX_RANGE_DAYS = 90;
    private static final int DEFAULT_HISTORY_DAYS = 7;
    private static final long MAX_IMAGE_BYTES = 10L * 1024 * 1024;
    private static final long MAX_AUDIO_BYTES = 10L * 1024 * 1024;
    private static final long MAX_VIDEO_BYTES = 20L * 1024 * 1024;
    // Mỗi sự cố: tối đa 5 ảnh + 1 video hiện trường
    private static final int MAX_IMAGES_PER_INCIDENT = 5;
    // Ảnh/video kèm lúc gửi SOS: FE tải lên ngay sau khi tạo ca
    private static final long INITIAL_MEDIA_MINUTES = 10;
    private static final int MAX_VIDEOS_PER_INCIDENT = 1;
    private static final Map<String, String> EXTENSION_BY_TYPE = Map.ofEntries(
            Map.entry("image/jpeg", "jpg"),
            Map.entry("image/png", "png"),
            Map.entry("image/webp", "webp"),
            Map.entry("image/gif", "gif"),
            Map.entry("video/mp4", "mp4"),
            Map.entry("video/webm", "webm"),
            Map.entry("video/quicktime", "mov"),
            Map.entry("audio/mpeg", "mp3"),
            Map.entry("audio/mp3", "mp3"),
            Map.entry("audio/wav", "wav"),
            Map.entry("audio/x-wav", "wav"),
            Map.entry("audio/ogg", "ogg"),
            Map.entry("audio/aac", "aac"),
            Map.entry("audio/m4a", "m4a"),
            Map.entry("audio/x-m4a", "m4a"),
            Map.entry("audio/mp4", "m4a"),
            Map.entry("audio/webm", "weba")
    );

    private final IncidentRepository incidentRepository;
    private final IncidentClaimRepository incidentClaimRepository;
    private final IncidentMediaRepository incidentMediaRepository;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final RescueStationRepository rescueStationRepository;
    private final AccountRepository accountRepository;
    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final com.sosvietnam.security.CurrentOfficer currentOfficer;
    private final GeoService geoService;
    private final MediaStorageService mediaStorageService;
    private final IncidentNotifier notifier;
    private final IncidentEscalationService escalationService;
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final com.sosvietnam.repository.IncidentDocumentRepository incidentDocumentRepository;
    private final com.sosvietnam.service.DemoModeService demoModeService;

    // Ca Công an cấp xã quá số phút này chưa ai nhận thì lên cấp tỉnh
    @Value("${app.incident.escalation-minutes:15}")
    private long escalationMinutes;
    // Ca chờ quá số phút này mà chưa ai nhận thì hệ thống tự huỷ (lý do NO_RESPONDER)
    @Value("${app.incident.auto-cancel-minutes:60}")
    private long autoCancelMinutes;
    // Bấm SOS lại trong khoảng này khi ca cũ còn mở thì quay lại ca cũ; ca mở cũ hơn coi là bỏ dở
    @Value("${app.incident.resume-hours:24}")
    private long resumeHours;
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private final SecureRandom secureRandom = new SecureRandom();

    // Deliberately not @Transactional: a failed boundary lookup must not mark the
    // transaction rollback-only and take the SOS down with it. save() has its own.
    @Override
    public CreateIncidentResponse createIncident(CreateIncidentRequest request) {
        AgencyType agency = request.getEmergencyType().getAgency();
        if (!agency.receivesIncidents()) {
            throw new SosException(HttpStatus.BAD_REQUEST,
                    "Cấp cứu y tế không gửi qua SOS: vui lòng gọi 115 hoặc bệnh viện gần nhất trong danh bạ");
        }

        CreateIncidentResponse reopened = reopenOpenSos(request);
        if (reopened != null) return reopened;

        String id = "SOS-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        GeoService.Area area = GeoService.Area.NONE;
        try {
            area = geoService.resolveArea(request.getLatitude(), request.getLongitude());
        } catch (Exception e) {
            // An SOS must never be lost because the boundary lookup failed.
            log.warn("Could not resolve jurisdiction for {}: {}", id, e.getMessage());
        }

        // Ca luôn thuộc xã chứa GPS (kể cả khi trụ sở xã khác gần hơn); trạm xuất phát gần nhất chọn lúc điều động

        // Phân luồng: chọn cấp nhận ca theo lực lượng và địa bàn
        LocalDateTime now = LocalDateTime.now();
        UnitLevel level = routeLevel(agency, area);
        LocalDateTime escalateAt = level == UnitLevel.WARD && agency.isEscalates()
                ? now.plusMinutes(escalationMinutes) : null;

        String citizenAccessToken = newAccessToken();

        Incident incident = Incident.builder()
                .id(id)
                .citizenName(request.getCitizenName())
                .phone(request.getPhone())
                .emergencyType(request.getEmergencyType())
                .status(IncidentStatus.PENDING)
                .description(request.getDescription())
                .incidentTag(firstTag(request.getIncidentTags()))
                .address(request.getAddress())
                .ward(area.ward())
                .province(area.province())
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .routedAgency(agency)
                .routedLevel(level)
                .escalateAt(escalateAt)
                .citizenTokenHash(CitizenTokens.sha256(citizenAccessToken))
                .lookupCode(newLookupCode())
                .createdAt(now)
                .build();

        Incident saved = incidentRepository.save(incident);
        log.info("🚨 [SOS ALERT] New Incident Registered: ID={}, Type={}, Location={}, {} -> {} {}",
                id, request.getEmergencyType(), saved.wardName(), saved.provinceName(), agency, level);

        escalationService.schedule(saved);
        notifier.notify(saved.getId(), IncidentEvent.INCIDENT_NEW);
        return new CreateIncidentResponse(IncidentResponse.from(saved, List.of(), List.of()), citizenAccessToken, false);
    }

    /** Tên FE tự điền khi người dân không khai (sos-request-modal DEFAULT_REPORTER_NAME, bản gốc), dạng AreaNames.key */
    private static final Set<String> PLACEHOLDER_NAMES = Set.of("nguoi dan can cuu ho", "nguoi dan can ho tro", "nguoi dan");

    /**
     * Mỗi số điện thoại chỉ có 1 SOS đang mở: bấm SOS lại khi ca cũ (tạo trong resume-hours) còn mở thì trả lại ca
     * đó với citizen token MỚI (token cũ hết hiệu lực) để FE vào thẳng màn hình gọi, không tạo ca mới.
     * - Ca chờ quá auto-cancel-minutes chưa ai nhận: huỷ (NO_RESPONDER) rồi cho tạo ca mới.
     * - Ca mở cũ hơn resume-hours (ca bỏ dở) không chặn, không mở lại.
     * - Lúc báo ca cũ có khai họ tên mà lần này khai tên khác: 409 (không cho người khác chiếm phiên theo dõi).
     * @return null khi không có ca nào để quay lại
     */
    private CreateIncidentResponse reopenOpenSos(CreateIncidentRequest request) {
        String phone = request.getPhone();
        if (phone == null || phone.isBlank()) return null;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expired = now.minusMinutes(autoCancelMinutes);
        LocalDateTime oldest = now.minusHours(resumeHours);
        for (Incident open : incidentRepository.findByPhoneAndStatusInOrderByCreatedAtDesc(phone.trim(), IncidentStatus.ACTIVE)) {
            if (open.getCreatedAt() == null || open.getCreatedAt().isBefore(oldest)) continue;
            boolean stale = open.getStatus() == IncidentStatus.PENDING && open.getHandlerId() == null
                    && open.getCreatedAt().isBefore(expired);
            if (stale && cancel(open.getId(), CancelReason.NO_RESPONDER, Set.of(IncidentStatus.PENDING))) continue;

            String declared = com.sosvietnam.util.AreaNames.key(open.getCitizenName());
            String given = com.sosvietnam.util.AreaNames.key(request.getCitizenName());
            if (declared != null && !PLACEHOLDER_NAMES.contains(declared) && given != null
                    && !PLACEHOLDER_NAMES.contains(given) && !declared.equals(given)) {
                throw new SosException(HttpStatus.CONFLICT, "Số điện thoại này đang có một tin báo SOS khác chưa kết thúc. "
                        + "Nếu cần hỗ trợ ngay, vui lòng gọi 113 / 114 / 115.");
            }
            String token = newAccessToken();
            open.setCitizenTokenHash(CitizenTokens.sha256(token));
            Incident saved = incidentRepository.save(open);
            log.info("🔁 [RESUME] SOS again from the phone of open incident {}: back to it (new access token)", saved.getId());
            return new CreateIncidentResponse(IncidentResponse.from(saved, List.of(), List.of()), token, true);
        }
        return null;
    }

    /** Chạy sau khi transaction commit (ngoài transaction thì chạy ngay) */
    private static void afterCommit(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }

    @Override
    @Transactional
    public boolean autoRelease(String id, UUID handlerId, String note) {
        LocalDateTime now = LocalDateTime.now();
        if (incidentRepository.release(id, handlerId, now) == 0) return false;
        incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, handlerId)
                .ifPresent(claim -> closeClaim(claim, now, ClaimEndReason.AUTO_RELEASED, null, note));
        Incident released = findIncident(id);
        afterCommit(() -> escalationService.schedule(released));
        log.info("⏏️ [AUTO-RELEASE] Incident {} back to the queue: holder {} offline", id, handlerId);
        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return true;
    }

    @Override
    @Transactional
    public IncidentQueueResponse transferAgency(String id, com.sosvietnam.model.payload.request.TransferIncidentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
        }
        if (incident.getStatus() != IncidentStatus.VERIFYING) {
            throw new SosException(HttpStatus.CONFLICT, "Chỉ chuyển lực lượng được khi đang xác minh (chưa điều động)");
        }
        AgencyType target = request.getAgency();
        AgencyType previous = incident.getRoutedAgency();
        if (target == null || !target.receivesIncidents()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Lực lượng này không nhận ca qua SOS");
        }
        if (target == previous) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Ca đang ở lực lượng này rồi");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < 5) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Ghi lý do chuyển lực lượng (ít nhất 5 ký tự)");
        }
        LocalDateTime now = LocalDateTime.now();
        GeoService.Area area = new GeoService.Area(incident.getProvince(), incident.getWard());
        UnitLevel level = routeLevel(target, area);
        incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, me.memberId())
                .ifPresent(claim -> closeClaim(claim, now, ClaimEndReason.TRANSFERRED, null,
                        "Chuyển sang " + target.getLabel() + ": " + reason));
        java.util.Arrays.stream(EmergencyType.values()).filter(t -> t.getAgency() == target).findFirst()
                .ifPresent(incident::setEmergencyType);
        incident.setRoutedAgency(target);
        incident.setRoutedLevel(level);
        incident.setStatus(IncidentStatus.PENDING);
        incident.setHandlerId(null);
        incident.setClaimedAt(null);
        incident.setEscalateAt(level == UnitLevel.WARD && target.isEscalates() ? now.plusMinutes(escalationMinutes) : null);
        incident.setEscalatedAt(null);
        incident.setEscalationReason(null);
        incident.setEscalationNote(null);
        incident.setEscalatedBy(null);
        incident.setTransferredFrom(previous);
        incident.setTransferReason(reason);
        incident.setTransferredBy(me.memberId());
        incident.setTransferredAt(now);
        incident.setUpdatedAt(now);
        Incident saved = incidentRepository.save(incident);
        afterCommit(() -> escalationService.schedule(saved));
        log.info("🔀 [TRANSFER] Incident {} moved {} -> {} {} by {}", id, previous, target, level, me.email());
        notifier.notifyRemovedFor(id, previous);
        notifier.notify(id, IncidentEvent.INCIDENT_NEW);
        return queueView(id, me);
    }

    /**
     * Cấp nhận ca: không biết tỉnh -> NATIONAL (chỉ trung tâm chỉ huy thấy); lực lượng trực ở xã (Công an)
     * mà xác định được xã và xã có cán bộ đang trực tuyến (có phiên STOMP) -> WARD; còn lại vào thẳng cấp tỉnh/phòng,
     * không để ca chờ 15 phút ở một xã không ai nhận được.
     */
    private UnitLevel routeLevel(AgencyType agency, GeoService.Area area) {
        return routeLevel(agency, area, true);
    }

    /** requireOnline=false: chỉ cần xã có tài khoản (phân luồng bù lúc khởi động, khi chưa ai kết nối). */
    private UnitLevel routeLevel(AgencyType agency, GeoService.Area area, boolean requireOnline) {
        if (area.province() == null) {
            return UnitLevel.NATIONAL;
        }
        if (agency.getIntakeLevel() == UnitLevel.WARD && area.ward() != null) {
            if (!requireOnline) {
                return accountRepository.existsByAgencyTypeAndUnitLevelAndWard_IdAndIsActiveTrue(
                        agency, UnitLevel.WARD, area.ward().getId()) ? UnitLevel.WARD : UnitLevel.PROVINCE;
            }
            Long wardId = area.ward().getId();
            boolean anyoneOnline = notifier.onlineOfficers().stream()
                    .anyMatch(o -> o.agency() == agency && o.level() == UnitLevel.WARD && wardId.equals(o.wardId()));
            if (anyoneOnline) {
                return UnitLevel.WARD;
            }
            log.info("📶 [ROUTING] No {} officer online in ward {} -> province", agency, area.ward().getName());
        }
        return UnitLevel.PROVINCE;
    }

    @Transactional
    @Override
    public IncidentResponse dispatchIncident(String id, DispatchRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (request == null || request.getStationId() == null) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chưa chọn trạm / lực lượng điều động");
        }
        RescueStation station = rescueStationRepository.findById(request.getStationId())
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy trạm ID: " + request.getStationId()));
        LocalDateTime now = LocalDateTime.now();
        // Một trạm chỉ ghi một lượt điều động cho mỗi ca (khoá chính incident_id + station_id)
        if (!incidentDispatchRepository.existsByIncident_IdAndStation_Id(id, station.getId())) {
            incidentDispatchRepository.save(IncidentDispatch.builder()
                    .incident(incident)
                    .station(station)
                    // lực lượng hỗ trợ; đơn vị chủ trì + tuyến đường đi qua PUT /dispatch-plan
                    .role(DispatchRole.SUPPORT)
                    .status(DispatchStatus.EN_ROUTE)
                    .departedAt(now)
                    .dispatchedBy(unitMemberRepository.getReferenceById(me.memberId()))
                    .unitLevel(me.level())
                    .note(request.getNote())
                    .dispatchedAt(now)
                    .build());
        }
        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setUpdatedAt(now);
        Incident updated = incidentRepository.save(incident);
        log.info("🚒 [RESOLVING] Incident {} dispatched station {} by {}", id, station.getId(), me.email());

        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return IncidentResponse.from(updated, incidentMediaRepository.findByIncident_IdOrderByCreatedAtAsc(id),
                incidentDispatchRepository.findByIncident_IdOrderByDispatchedAtAsc(id));
    }

    // ------------------------------------------------------------------ hàng đợi + nhận ca

    @Override
    public ListResponse getQueue(int pageNo, int pageSize, Integer days) {
        checkPage(pageNo, pageSize);
        LocalDateTime since = sinceDays(days, null);
        OfficerPrincipal me = currentOfficer();
        // Ca do BE bản cũ tạo (chưa phân luồng) thì phân luồng ngay để không bị lọt khỏi hàng đợi
        routeUnroutedIncidents();

        List<Incident> visible = withDelegated(me, visibleIncidents(me, IncidentStatus.ACTIVE, since).toList(), since);
        List<Incident> slice = pageOf(visible, pageNo, pageSize);
        return listResponse(notifier.toQueueViews(slice, me), visible.size(), pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public ListResponse getHistory(Integer days, String query, AgencyType agency, int pageNo, int pageSize) {
        checkPage(pageNo, pageSize);
        LocalDateTime since = sinceDays(days, DEFAULT_HISTORY_DAYS);
        OfficerPrincipal me = currentOfficer();
        String q = AreaNames.key(query);

        List<Incident> visible = visibleIncidents(me, IncidentStatus.CLOSED, since)
                .filter(i -> agency == null || !me.isAdmin() || agency == i.getRoutedAgency())
                .filter(i -> q == null || matches(i, q))
                .toList();
        List<Incident> page = pageOf(visible, pageNo, pageSize);
        Map<String, com.sosvietnam.model.entity.IncidentDocument> documents = new HashMap<>();
        if (!page.isEmpty()) {
            incidentDocumentRepository.findByIncidentIds(page.stream().map(Incident::getId).toList())
                    .forEach(d -> documents.put(d.getIncidentId(), d));
        }
        List<IncidentHistoryResponse> content = page.stream()
                .map(i -> {
                    IncidentHistoryResponse row = IncidentHistoryResponse.from(i, lastHandlerName(i),
                            incidentDispatchRepository.countByIncident_Id(i.getId()));
                    var doc = documents.get(i.getId());
                    row.setDocumentStatus(doc == null ? "NONE" : doc.locked() ? "SIGNED" : "DRAFT");
                    return row;
                })
                .toList();
        return listResponse(content, visible.size(), pageNo, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentDetailResponse getDetail(String id) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!IncidentAccess.canView(me, incident) && !IncidentAccess.isDelegatedUnit(me,
                incidentDispatchRepository.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(id, DispatchRole.PRIMARY)
                        .orElse(null))) {
            throw new SosException(HttpStatus.FORBIDDEN, "Ca " + id + " không thuộc phạm vi của đơn vị bạn");
        }
        return IncidentDetailResponse.from(incident,
                incidentMediaRepository.findByIncident_IdOrderByCreatedAtAsc(id),
                incidentDispatchRepository.findByIncident_IdOrderByDispatchedAtAsc(id),
                incidentClaimRepository.findByIncident_IdOrderByClaimedAtAsc(id));
    }

    /** Cán bộ cấp xã: thêm các ca cấp trên đang uỷ quyền cho đơn vị mình (bước 2 điều động), mới nhất trước. */
    private List<Incident> withDelegated(OfficerPrincipal me, List<Incident> visible, LocalDateTime since) {
        if (me.level() != UnitLevel.WARD || me.agency() == null || me.wardId() == null) return visible;
        Set<String> seen = visible.stream().map(Incident::getId).collect(java.util.stream.Collectors.toSet());
        List<Incident> extra = incidentDispatchRepository
                .findDelegatedToWard(me.agency(), me.wardId(), DispatchStatus.DELEGATED, IncidentStatus.ACTIVE).stream()
                .map(IncidentDispatch::getIncident)
                .filter(i -> !seen.contains(i.getId()))
                .filter(i -> since == null || !i.getCreatedAt().isBefore(since))
                .filter(this::shown)
                .toList();
        if (extra.isEmpty()) return visible;
        return Stream.concat(visible.stream(), extra.stream())
                .sorted(java.util.Comparator.comparing(Incident::getCreatedAt).reversed())
                .toList();
    }

    /** Ca của lực lượng mình (ADMIN: mọi lực lượng) trong địa bàn được xem, mới nhất trước. */
    private Stream<Incident> visibleIncidents(OfficerPrincipal me, Set<IncidentStatus> statuses,
                                                               LocalDateTime since) {
        List<AgencyType> agencies = me.isAdmin() ? List.of(AgencyType.values()) : List.of(me.agency());
        List<Incident> rows = since == null
                ? incidentRepository.findByStatusInAndRoutedAgencyInOrderByCreatedAtDesc(statuses, agencies)
                : incidentRepository.findByStatusInAndRoutedAgencyInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        statuses, agencies, since);
        return rows.stream().filter(i -> IncidentAccess.canView(me, i)).filter(this::shown);
    }

    /** Công tắc "ẩn ca demo" (Cài đặt): ca [DEMO] không vào hàng đợi / lịch sử. */
    private boolean shown(Incident incident) {
        return !demoModeService.hidden() || !DemoIncidents.isDemo(incident);
    }

    private static boolean matches(Incident i, String q) {
        return Stream.of(i.getId(), i.getCitizenName(), i.getPhone(), i.getAddress(), i.wardName())
                .map(AreaNames::key)
                .anyMatch(v -> v != null && v.contains(q));
    }

    private String lastHandlerName(Incident i) {
        return incidentClaimRepository.findFirstByIncident_IdOrderByClaimedAtDesc(i.getId())
                .map(c -> c.getMember().displayName())
                .orElse(null);
    }

    private static void checkPage(int pageNo, int pageSize) {
        if (pageNo < 0 || pageSize < 1 || pageSize > MAX_QUEUE_PAGE_SIZE) {
            throw new SosException("pageNo phải >= 0, pageSize từ 1 đến " + MAX_QUEUE_PAGE_SIZE);
        }
    }

    /** Mốc "N ngày trước"; days null thì dùng fallback (null = không giới hạn). */
    private static LocalDateTime sinceDays(Integer days, Integer fallback) {
        Integer d = days != null ? days : fallback;
        if (d == null) return null;
        if (d < 1 || d > MAX_RANGE_DAYS) {
            throw new SosException("days phải từ 1 đến " + MAX_RANGE_DAYS);
        }
        return LocalDateTime.now().minusDays(d);
    }

    private static <T> List<T> pageOf(List<T> all, int pageNo, int pageSize) {
        int from = (int) Math.min((long) pageNo * pageSize, all.size());
        int to = Math.min(from + pageSize, all.size());
        return all.subList(from, to);
    }

    private static ListResponse listResponse(List<?> content, int total, int pageNo, int pageSize) {
        int totalPages = (total + pageSize - 1) / pageSize;
        ListResponse page = new ListResponse();
        page.setContent(content);
        page.setPageNo(pageNo);
        page.setPageSize(pageSize);
        page.setTotalElements(total);
        page.setTotalPages(totalPages);
        page.setLast(pageNo >= totalPages - 1);
        return page;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void routeUnroutedOnStartup() {
        try {
            int n = routeUnroutedIncidents();
            if (n > 0) log.info("🧭 [ROUTING] Routed {} incident(s) created before routing existed", n);
        } catch (Exception e) {
            log.error("❌ [ROUTING] Could not route old incidents: {}", e.getMessage(), e);
        }
    }

    @Override
    public synchronized int routeUnroutedIncidents() {
        List<Incident> unrouted = incidentRepository.findByStatusInAndRoutedAgencyIsNull(IncidentStatus.ACTIVE);
        for (Incident incident : unrouted) {
            AgencyType agency = incident.getEmergencyType() == null
                    ? AgencyType.POLICE : incident.getEmergencyType().getAgency();
            GeoService.Area area = new GeoService.Area(incident.getProvince(), incident.getWard());
            UnitLevel level = agency.receivesIncidents() ? routeLevel(agency, area, false) : UnitLevel.NATIONAL;
            incident.setRoutedAgency(agency);
            incident.setRoutedLevel(level);
            // Hạn leo thang tính từ lúc tạo ca như ca mới; đã quá hạn thì lịch hẹn leo thang ngay
            if (level == UnitLevel.WARD && agency.isEscalates() && incident.getEscalateAt() == null) {
                LocalDateTime created = incident.getCreatedAt() == null ? LocalDateTime.now() : incident.getCreatedAt();
                incident.setEscalateAt(created.plusMinutes(escalationMinutes));
            }
            Incident saved = incidentRepository.save(incident);
            log.info("🧭 [ROUTING] Old incident {} -> {} {}", saved.getId(), agency, level);
            escalationService.schedule(saved);
            notifier.notify(saved.getId(), IncidentEvent.INCIDENT_NEW);
        }
        return unrouted.size();
    }

    @Transactional
    @Override
    public IncidentQueueResponse claimIncident(String id) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!IncidentAccess.canHandle(me, incident)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Ca này không thuộc cấp đơn vị của bạn (chỉ được theo dõi)");
        }
        if (incident.getHandlerId() != null || incident.getStatus() != IncidentStatus.PENDING) {
            throw new SosException(HttpStatus.CONFLICT, "Ca đã có cán bộ khác tiếp nhận");
        }
        if (incidentRepository.existsByHandlerIdAndStatusIn(me.memberId(), IncidentStatus.ACTIVE)) {
            throw new SosException(HttpStatus.CONFLICT, "Bạn đang giữ một ca khác: hoàn tất hoặc nhả ca đó trước");
        }

        LocalDateTime now = LocalDateTime.now();
        int claimed;
        try {
            claimed = incidentRepository.claim(id, me.memberId(), incident.getRoutedLevel(), now);
        } catch (DataIntegrityViolationException e) {
            // ux_incidents_active_handler: cùng người bấm nhận 2 ca gần như đồng thời
            throw new SosException(HttpStatus.CONFLICT, "Bạn đang giữ một ca khác: hoàn tất hoặc nhả ca đó trước");
        }
        if (claimed == 0) {
            throw new SosException(HttpStatus.CONFLICT, "Ca vừa được cán bộ khác tiếp nhận");
        }
        incidentClaimRepository.save(IncidentClaim.builder()
                .incident(incidentRepository.getReferenceById(id))
                .member(unitMemberRepository.getReferenceById(me.memberId()))
                .claimedAt(now)
                .build());
        log.info("🙋 [CLAIM] Incident {} claimed by {}", id, me.email());

        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return queueView(id, me);
    }

    @Transactional
    @Override
    public IncidentQueueResponse releaseIncident(String id, ReleaseIncidentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
        }
        LocalDateTime now = LocalDateTime.now();
        String note = request == null ? null : request.getNote();
        UUID toId = request == null ? null : request.getToMemberId();
        IncidentClaim myClaim = incidentClaimRepository
                .findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, me.memberId())
                .orElse(null);

        if (toId != null) {
            if (note == null || note.trim().length() < 5) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do giao ca (ít nhất 5 ký tự)");
            }
            com.sosvietnam.model.entity.UnitMember target = unitMemberRepository.findById(toId)
                    .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy cán bộ được giao"));
            OfficerPrincipal to = OfficerPrincipal.of(target);
            if (toId.equals(me.memberId()) || !to.isStaff() || !target.isActive() || target.isLocked()
                    || !target.getAccount().isActive() || !IncidentAccess.sameUnit(me, to)) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Chỉ giao được cho cán bộ khác cùng đơn vị");
            }
            if (incidentRepository.existsByHandlerIdAndStatusIn(toId, IncidentStatus.ACTIVE)) {
                throw new SosException(HttpStatus.CONFLICT, target.displayName() + " đang giữ một ca khác");
            }
            if (incidentRepository.handOver(id, me.memberId(), toId, now) == 0) {
                throw new SosException(HttpStatus.CONFLICT, "Ca đã thay đổi, tải lại hàng đợi");
            }
            closeClaim(myClaim, now, ClaimEndReason.HANDED_OVER, target, note);
            incidentClaimRepository.save(IncidentClaim.builder()
                    .incident(incidentRepository.getReferenceById(id))
                    .member(target)
                    .claimedAt(now)
                    .build()); // lý do giao ca chỉ nằm ở lượt của người giao
            log.info("🤝 [HANDOVER] Incident {} handed from {} to {} ({})", id, me.fullName(), target.getFullName(), me.email());
        } else {
            if (incidentRepository.release(id, me.memberId(), now) == 0) {
                throw new SosException(HttpStatus.CONFLICT, "Ca đã thay đổi, tải lại hàng đợi");
            }
            closeClaim(myClaim, now, ClaimEndReason.RELEASED, null, note);
            log.info("↩️ [RELEASE] Incident {} released by {}", id, me.email());
            // Đồng hồ leo thang tính tiếp từ lúc tạo ca: quá hạn rồi thì leo thang ngay
            Incident released = findIncident(id);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    escalationService.schedule(released);
                }
            });
        }

        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return queueView(id, me);
    }

    @Override
    public List<HandoverCandidateResponse> getHandoverCandidates(String id) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
        }
        List<com.sosvietnam.model.entity.UnitMember> colleagues = unitMemberRepository.findActiveOfficers(me.agency(), me.level()).stream()
                .filter(m -> !me.is(m.getId()) && !m.isLocked())
                .filter(m -> IncidentAccess.sameUnit(me, OfficerPrincipal.of(m)))
                .toList();
        Set<UUID> busy = incidentRepository
                .findByHandlerIdInAndStatusIn(colleagues.stream().map(com.sosvietnam.model.entity.UnitMember::getId).toList(), IncidentStatus.ACTIVE)
                .stream().map(Incident::getHandlerId).collect(Collectors.toSet());
        Set<UUID> online = notifier.onlineMemberIds();
        return colleagues.stream()
                .map(m -> new HandoverCandidateResponse(m.getId(), m.displayName(), online.contains(m.getId()), busy.contains(m.getId())))
                .sorted(Comparator.comparing(HandoverCandidateResponse::isBusy)
                        .thenComparing(c -> !c.isOnline())
                        .thenComparing(HandoverCandidateResponse::getFullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private void closeClaim(IncidentClaim claim, LocalDateTime now, ClaimEndReason reason,
                            com.sosvietnam.model.entity.UnitMember handedTo, String note) {
        if (claim == null) return;
        claim.setReleasedAt(now);
        claim.setEndReason(reason);
        claim.setHandedTo(handedTo);
        if (note != null && !note.isBlank()) claim.setNote(note.trim());
        incidentClaimRepository.save(claim);
    }

    private IncidentQueueResponse queueView(String id, OfficerPrincipal me) {
        return notifier.toQueueViews(List.of(findIncident(id)), me).get(0);
    }

    private OfficerPrincipal currentOfficer() {
        return currentOfficer.require();
    }

    @Transactional
    @Override
    public List<IncidentResponse.MediaResponse> addMedia(String id, String citizenAccessToken, List<MultipartFile> files) {
        Incident incident = findIncident(id);
        verifyCitizenToken(incident, citizenAccessToken);

        if (files == null || files.isEmpty()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chưa chọn ảnh/video nào");
        }
        // Ảnh/video gửi kèm lúc báo tin (ca vừa tạo) hoặc bổ sung khi đã có cán bộ nhận ca; ca đóng thì thôi
        boolean justCreated = incident.getStatus() == IncidentStatus.PENDING && incident.getCreatedAt() != null
                && incident.getCreatedAt().isAfter(LocalDateTime.now().minusMinutes(INITIAL_MEDIA_MINUTES));
        if (!justCreated && !IncidentStatus.HELD.contains(incident.getStatus())) {
            throw new SosException(HttpStatus.CONFLICT, IncidentStatus.ACTIVE.contains(incident.getStatus())
                    ? "Chỉ bổ sung ảnh/video được khi đã có cán bộ tiếp nhận tin báo"
                    : "Tin báo đã kết thúc, không bổ sung ảnh/video được nữa");
        }
        if (incidentMediaRepository.countByIncident_Id(id) + files.size() > MAX_MEDIA_PER_INCIDENT) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Tối đa " + MAX_MEDIA_PER_INCIDENT + " ảnh/video cho mỗi sự cố");
        }

        List<IncidentMedia> existing = incidentMediaRepository.findByIncident_IdOrderByCreatedAtAsc(id);
        long images = existing.stream().filter(m -> m.getKind() == MediaKind.IMAGE).count()
                + files.stream().filter(f -> Objects.requireNonNullElse(f.getContentType(), "").toLowerCase().startsWith("image/")).count();
        long videos = existing.stream().filter(m -> m.getKind() == MediaKind.VIDEO).count()
                + files.stream().filter(f -> Objects.requireNonNullElse(f.getContentType(), "").toLowerCase().startsWith("video/")).count();
        if (images > MAX_IMAGES_PER_INCIDENT || videos > MAX_VIDEOS_PER_INCIDENT) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Mỗi sự cố tối đa " + MAX_IMAGES_PER_INCIDENT + " ảnh và "
                    + MAX_VIDEOS_PER_INCIDENT + " video");
        }

        List<IncidentMedia> added = new ArrayList<>();
        for (MultipartFile file : files) {
            String contentType = Objects.requireNonNullElse(file.getContentType(), "").toLowerCase();
            String extension = EXTENSION_BY_TYPE.get(contentType);
            if (extension == null) {
                throw new SosException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Định dạng không hỗ trợ: " + file.getOriginalFilename());
            }
            boolean isVideo = contentType.startsWith("video/");
            boolean isAudio = contentType.startsWith("audio/");
            long maxSize = isVideo ? MAX_VIDEO_BYTES : (isAudio ? MAX_AUDIO_BYTES : MAX_IMAGE_BYTES);
            if (file.getSize() > maxSize) {
                String label = isVideo ? "Video tối đa 20MB: " : (isAudio ? "File ghi âm tối đa 10MB: " : "Ảnh tối đa 10MB: ");
                throw new SosException(HttpStatus.PAYLOAD_TOO_LARGE, label + file.getOriginalFilename());
            }

            String sha256 = sha256Of(file);
            String fileName = mediaStorageService.store(mediaFolder(id), file, extension);
            added.add(incidentMediaRepository.save(IncidentMedia.builder()
                    .incident(incident)
                    .kind(isVideo ? MediaKind.VIDEO : (isAudio ? MediaKind.AUDIO : MediaKind.IMAGE))
                    .fileName(fileName)
                    .storageKey(MediaKeys.key(mediaFolder(id), fileName))
                    .originalName(file.getOriginalFilename())
                    .contentType(contentType)
                    .sizeBytes(file.getSize())
                    .sha256(sha256)
                    .uploadedBy(UploaderType.CITIZEN)
                    .createdAt(LocalDateTime.now())
                    .build()));
        }
        log.info("📎 [MEDIA] {} file(s) attached to incident {}", added.size(), id);

        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return added.stream().map(item -> IncidentResponse.toMediaResponse(id, item)).toList();
    }

    @Override
    public MediaAccess loadMedia(String id, String fileName) {
        incidentMediaRepository.findByIncident_IdAndFileName(id, fileName)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy tệp"));
        return mediaStorageService.load(mediaFolder(id), fileName);
    }

    @Transactional
    @Override
    public IncidentQueueResponse requestDispatch(String id, UUID fieldMemberId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
        }
        if (incidentRepository.startResolving(id, me.memberId(), fieldMemberId, LocalDateTime.now()) == 0) {
            throw new SosException(HttpStatus.CONFLICT, "Ca không còn ở bước xác minh, tải lại hàng đợi");
        }
        log.info("🚓 [RESOLVING] Incident {} verified by {}, dispatch requested", id, me.email());
        notifier.notify(id, IncidentEvent.INCIDENT_UPDATED);
        return queueView(id, me);
    }

    @Transactional
    @Override
    public IncidentTrackingResponse escalateNow(String id, String citizenAccessToken, String reason) {
        Incident incident = findIncident(id);
        boolean byCitizen = citizenAccessToken != null && !citizenAccessToken.isBlank();
        Set<IncidentStatus> from;
        String who;
        if (byCitizen) {
            verifyCitizenToken(incident, citizenAccessToken);
            // Người dân yêu cầu được khi ca cấp xã đã quá 15 phút mà chưa điều động lực lượng:
            // chưa ai nhận (hẹn giờ tự leo thang bị lỡ) hoặc cán bộ nhận rồi mà vẫn đang xác minh
            boolean overdue = incident.getEscalateAt() != null && !incident.getEscalateAt().isAfter(LocalDateTime.now());
            boolean open = incident.getStatus() == IncidentStatus.VERIFYING || incident.getStatus() == IncidentStatus.PENDING;
            if (!open || !overdue) {
                throw new SosException(HttpStatus.CONFLICT, incident.getStatus() == IncidentStatus.RESOLVING
                        ? "Lực lượng đã được điều động, không chuyển lên cấp tỉnh được nữa"
                        : "Chỉ yêu cầu chuyển lên cấp tỉnh được sau 15 phút kể từ lúc báo tin");
            }
            if (incident.getStatus() == IncidentStatus.PENDING) {
                if (incident.getRoutedLevel() != UnitLevel.WARD || incident.getEscalatedAt() != null
                        || !escalationService.escalateIfDue(id)) {
                    throw new SosException(HttpStatus.CONFLICT, "Ca đã thay đổi, tải lại để xem trạng thái mới");
                }
                Incident moved = findIncident(id);
                moved.setEscalationReason(com.sosvietnam.model.payload.enums.EscalationReason.CITIZEN_REQUEST);
                incidentRepository.save(moved);
                log.info("⚡ [ESCALATION] Incident {} moved to province now (by citizen, nobody took it)", id);
                return tracking(moved);
            }
            from = Set.of(IncidentStatus.VERIFYING);
            who = "citizen";
        } else {
            OfficerPrincipal me = currentOfficer();
            if (!me.memberId().equals(incident.getHandlerId())) {
                throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
            }
            if (reason == null || reason.trim().length() < 5) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Ghi lý do chuyển lên cấp tỉnh (ít nhất 5 ký tự)");
            }
            from = IncidentStatus.HELD;
            who = me.email();
        }
        if (incident.getRoutedLevel() != UnitLevel.WARD || incident.getEscalatedAt() != null) {
            throw new SosException(HttpStatus.CONFLICT, "Ca không ở cấp xã/phường nên không chuyển lên tỉnh được");
        }
        UUID handlerId = incident.getHandlerId();
        LocalDateTime now = LocalDateTime.now();
        if (handlerId == null || incidentRepository.escalateHeld(id, handlerId, from, now) == 0) {
            throw new SosException(HttpStatus.CONFLICT, "Ca đã thay đổi, tải lại để xem trạng thái mới");
        }
        String note = byCitizen ? "Người báo tin yêu cầu chuyển lên cấp tỉnh" : reason.trim();
        incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, handlerId)
                .ifPresent(claim -> closeClaim(claim, now, ClaimEndReason.ESCALATED, null, note));
        stopWardDispatch(id, now);
        Incident moved = findIncident(id);
        moved.setFieldMemberId(null); // người phụ trách do cán bộ xã chọn, tỉnh chọn lại
        moved.setEscalationReason(byCitizen ? com.sosvietnam.model.payload.enums.EscalationReason.CITIZEN_REQUEST
                : com.sosvietnam.model.payload.enums.EscalationReason.OFFICER_REQUEST);
        moved.setEscalationNote(byCitizen ? null : reason.trim());
        moved.setEscalatedBy(byCitizen ? null : handlerId);
        incidentRepository.save(moved);
        log.info("⚡ [ESCALATION] Incident {} moved to province now (by {})", id, who);
        notifier.notify(id, IncidentEvent.INCIDENT_ESCALATED);
        return tracking(findIncident(id));
    }

    /** Ca lên tỉnh lúc xã đã chọn tuyến (RESOLVING): ngắt lượt của xã, không thì cán bộ tỉnh mở lại lượt cũ và kẹt (409 khi uỷ quyền). */
    static final String ESCALATED_DISPATCH_REASON = "Ca đã chuyển lên cấp tỉnh";

    private void stopWardDispatch(String id, LocalDateTime now) {
        incidentDispatchRepository.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(
                        id, DispatchRole.PRIMARY, DispatchStatus.REJECTED)
                .filter(d -> d.getStatus() != DispatchStatus.DONE)
                .ifPresent(d -> {
                    // REJECTED: tỉnh bắt đầu lại từ bước 1, lượt cũ vẫn giữ để tra cứu; mô phỏng tự dừng khi hết EN_ROUTE
                    d.setStatus(DispatchStatus.REJECTED);
                    d.setRejectReason(ESCALATED_DISPATCH_REASON);
                    d.setRespondedAt(now);
                    d.setUpdatedAt(now);
                    incidentDispatchRepository.save(d);
                });
    }

    @Override
    @Transactional(readOnly = true) // handler account + station ward/province are LAZY (open-in-view=false)
    public IncidentLookupResponse lookup(IncidentLookupRequest request) {
        // Sai mã hay sai SĐT đều trả cùng một câu: không cho dò mã nào đang tồn tại
        SosException notFound = new SosException(HttpStatus.NOT_FOUND, "Mã tra cứu hoặc số điện thoại không đúng");
        String code = LookupCodes.normalize(request.getLookupCode());
        if (code == null) throw notFound;
        Incident incident = incidentRepository.findByLookupCode(code).orElseThrow(() -> notFound);
        String phone = incident.getPhone();
        if (phone == null || !phone.endsWith(request.getPhoneLast4())) throw notFound;
        List<IncidentDispatch> dispatches = incidentDispatchRepository.findByIncident_IdOrderByDispatchedAtAsc(incident.getId());
        Map<Long, IncidentRoute> routes = new HashMap<>();
        for (IncidentDispatch d : dispatches) {
            if (d.getId() == null) continue;
            incidentRouteRepository.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(d.getId())
                    .ifPresent(r -> routes.put(d.getId(), r));
        }
        return IncidentLookupResponse.from(incident, dispatches,
                incidentClaimRepository.findFirstByIncident_IdOrderByClaimedAtDesc(incident.getId()).orElse(null),
                incidentReportRepository.findById(incident.getId()).orElse(null), routes);
    }

    @Override
    public IncidentTrackingResponse getTracking(String id, String citizenAccessToken) {
        Incident incident = findIncident(id);
        verifyCitizenToken(incident, citizenAccessToken);
        return tracking(incident);
    }

    @Transactional
    @Override
    public IncidentTrackingResponse cancelByCitizen(String id, String citizenAccessToken, CancelIncidentRequest request) {
        Incident incident = findIncident(id);
        verifyCitizenToken(incident, citizenAccessToken);
        CancelReason reason = request == null ? null : request.getReason();
        if (reason != null && !reason.isCitizenChoice()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Lý do huỷ không hợp lệ");
        }
        if (!cancel(id, reason, IncidentStatus.ACTIVE)) {
            throw new SosException(HttpStatus.CONFLICT, "Tin báo đã kết thúc hoặc đã được huỷ trước đó");
        }
        log.info("🛑 [CANCEL] Incident {} cancelled by citizen ({})", id, reason);
        return tracking(findIncident(id));
    }

    // Lúc khởi động và mỗi phút sau đó: ca chờ quá auto-cancel-minutes chưa ai nhận thì tự huỷ (NO_RESPONDER),
    // người dân đang mở màn hình chờ nhận INCIDENT_CANCELLED qua STOMP
    @EventListener(ApplicationReadyEvent.class)
    @org.springframework.scheduling.annotation.Scheduled(initialDelay = 60_000, fixedDelay = 60_000)
    public void cancelStaleOnStartup() {
        try {
            int n = cancelStaleIncidents();
            if (n > 0) {
                log.info("🛑 [AUTO-CANCEL] Cancelled {} incident(s) waiting over {} min with no responder", n, autoCancelMinutes);
            }
        } catch (Exception e) {
            log.error("❌ [AUTO-CANCEL] Could not cancel stale incidents: {}", e.getMessage(), e);
        }
    }

    @Override
    public int cancelStaleIncidents() {
        LocalDateTime before = LocalDateTime.now().minusMinutes(autoCancelMinutes);
        int cancelled = 0;
        for (Incident incident : incidentRepository
                .findByStatusAndHandlerIdIsNullAndCreatedAtBefore(IncidentStatus.PENDING, before)) {
            // Chỉ ca vẫn đang chờ: ai vừa kịp nhận thì UPDATE có điều kiện bỏ qua ca đó
            if (cancel(incident.getId(), CancelReason.NO_RESPONDER, Set.of(IncidentStatus.PENDING))) {
                cancelled++;
                log.info("🛑 [AUTO-CANCEL] Incident {} (created {}) had no responder", incident.getId(), incident.getCreatedAt());
            }
        }
        return cancelled;
    }

    /**
     * Huỷ ca nếu status còn trong `fromStatuses`; đóng lượt giữ ca đang mở (nếu có) và báo hàng đợi gỡ ca.
     * @return false khi ca đã đóng / đổi trạng thái trước đó
     */
    @Transactional
    @Override
    public void cancelByOfficer(String id, String reason) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(id);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đang giữ ca mới huỷ ca được");
        }
        LocalDateTime now = LocalDateTime.now();
        if (incidentRepository.cancel(id, CancelReason.OFFICER_CANCELLED, IncidentStatus.HELD, now) == 0) {
            throw new SosException(HttpStatus.CONFLICT, "Ca đã thay đổi, tải lại hàng đợi");
        }
        incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, me.memberId())
                .ifPresent(claim -> closeClaim(claim, now, ClaimEndReason.CLOSED, null, "Cán bộ huỷ ca: " + reason.trim()));
        log.info("🗑️ [OFFICER CANCEL] Incident {} by {}: {}", id, me.email(), reason.trim());
        notifier.notify(id, IncidentEvent.INCIDENT_REMOVED);
    }

    private boolean cancel(String id, CancelReason reason, Set<IncidentStatus> fromStatuses) {
        LocalDateTime now = LocalDateTime.now();
        if (incidentRepository.cancel(id, reason, fromStatuses, now) == 0) return false;
        Incident cancelled = findIncident(id);
        if (cancelled.getHandlerId() != null) {
            String note = "Ca bị huỷ" + (reason == null ? "" : ": " + reason.getLabel());
            incidentClaimRepository
                    .findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(id, cancelled.getHandlerId())
                    .ifPresent(claim -> closeClaim(claim, now, ClaimEndReason.CLOSED, null, note));
        }
        notifier.notify(id, IncidentEvent.INCIDENT_REMOVED);
        return true;
    }

    private IncidentTrackingResponse tracking(Incident incident) {
        String handlerName = incident.getHandlerId() == null ? null
                : unitMemberRepository.findById(incident.getHandlerId()).map(com.sosvietnam.model.entity.UnitMember::displayName).orElse(null);
        return IncidentTrackingResponse.from(incident, handlerName, com.sosvietnam.model.entity.IncidentDocument
                .statusOf(incidentDocumentRepository.findById(incident.getId()).orElse(null)));
    }

    private Incident findIncident(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy vụ việc ID: " + id));
    }

    private void verifyCitizenToken(Incident incident, String token) {
        if (token == null || token.isBlank()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Thiếu mã truy cập của người báo tin");
        }
        if (!CitizenTokens.matches(incident, token)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
        }
    }

    /** SHA-256 nội dung file (bằng chứng không bị sửa sau khi tải lên). */
    private static String sha256Of(MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[64 * 1024];
            for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException e) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Không đọc được tệp: " + file.getOriginalFilename());
        }
    }

    private static String mediaFolder(String incidentId) {
        return "incidents/" + incidentId;
    }

    /** Emoji của ô gợi ý chỉ để hiển thị trên form: lưu nhãn chữ thuần ("🚨 Bị cướp giật" -> "Bị cướp giật") */
    private static final java.util.regex.Pattern EMOJI =
            java.util.regex.Pattern.compile("[\\p{So}\\p{Sk}\\p{Cs}\\x{FE0F}\\x{200D}\\x{20E3}]+");

    public static String stripEmoji(String text) {
        return text == null ? null : EMOJI.matcher(text).replaceAll("").replaceAll("\\s+", " ").trim();
    }

    /** Người dân chỉ chọn 1 gợi ý: lấy nhãn đầu tiên khác rỗng (đã bỏ emoji), không có thì null. */

    private static String firstTag(List<String> tags) {
        if (tags == null) return null;
        return tags.stream()
                .filter(Objects::nonNull)
                .map(IncidentServiceImpl::stripEmoji)
                .filter(t -> !t.isEmpty())
                .findFirst()
                .orElse(null);
    }

    /** Mã tra cứu chưa có ca nào dùng (31^8 tổ hợp, trùng gần như không xảy ra). */
    private String newLookupCode() {
        for (int i = 0; i < 5; i++) {
            String code = LookupCodes.generate();
            if (!incidentRepository.existsByLookupCode(code)) return code;
        }
        throw new SosException(HttpStatus.INTERNAL_SERVER_ERROR, "Không tạo được mã tra cứu, vui lòng thử lại");
    }

    private String newAccessToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
