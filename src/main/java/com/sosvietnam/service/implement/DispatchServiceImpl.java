package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.*;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.DispatchPlanRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.repository.*;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.DispatchService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.OfficerService;
import com.sosvietnam.model.entity.IncidentReport;
import com.sosvietnam.model.payload.request.FinishIncidentRequest;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import java.util.List;
import java.util.UUID;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.IncidentAccess;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchServiceImpl implements DispatchService {

    private final IncidentRepository incidentRepository;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final RescueStationRepository rescueStationRepository;
    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final com.sosvietnam.security.CurrentOfficer currentOfficer;
    private final IncidentNotifier notifier;
    private final OfficerService officerService;
    private final IncidentClaimRepository incidentClaimRepository;
    private final IncidentReportRepository incidentReportRepository;

    // CHỈ ĐỂ TEST: chế độ ca demo đang bật thì cho "Kết thúc ngay" bỏ qua xác nhận của người dân
    @org.springframework.beans.factory.annotation.Value("${app.demo.incidents.enabled:false}")
    private boolean demoMode;

    @Transactional(readOnly = true)
    @Override
    public DispatchPlanResponse getPlan(String incidentId, String citizenAccessToken) {
        Incident incident = findIncident(incidentId);
        if (citizenAccessToken != null && !citizenAccessToken.isBlank()) {
            if (!CitizenTokens.matches(incident, citizenAccessToken)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
            }
        } else {
            OfficerPrincipal me = currentOfficer();
            if (!IncidentAccess.canView(me, incident) && !IncidentAccess.isDelegatedUnit(me, latestPrimary(incidentId))) {
                throw new SosException(HttpStatus.FORBIDDEN, "Ca này không thuộc phạm vi của bạn");
            }
        }
        IncidentDispatch latest = latestPrimary(incidentId);
        return latest == null ? null : DispatchPlanResponse.of(latest, currentRoute(latest));
    }

    @Transactional
    @Override
    public DispatchPlanResponse savePlan(String incidentId, DispatchPlanRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đang giữ ca mới điều động được");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVING) {
            throw new SosException(HttpStatus.CONFLICT, "Ca chưa ở bước điều động (cần Yêu cầu điều động trước)");
        }
        currentPrimary(incidentId).ifPresent(DispatchServiceImpl::requireAnswered);
        RescueStation station = rescueStationRepository.findById(request.getStationId())
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy trạm ID: " + request.getStationId()));
        if (incident.getRoutedAgency() != null && station.getAgencyType() != incident.getRoutedAgency()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Trạm không thuộc lực lượng xử lý ca này");
        }
        if (station.getLatitude() == null || station.getLongitude() == null) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Trạm chưa có toạ độ");
        }

        LocalDateTime now = LocalDateTime.now();
        DispatchStatus status = request.isDelegate() ? DispatchStatus.PENDING_ACCEPT : DispatchStatus.EN_ROUTE;
        IncidentDispatch dispatch = currentPrimary(incidentId).orElseGet(() -> IncidentDispatch.builder()
                .incident(incident)
                .role(DispatchRole.PRIMARY)
                .build());
        dispatch.setStation(station);
        dispatch.setStatus(status);
        dispatch.setDispatchedBy(unitMemberRepository.getReferenceById(me.memberId()));
        dispatch.setUnitLevel(me.level());
        dispatch.setDispatchedAt(now);
        dispatch.setDepartedAt(status == DispatchStatus.EN_ROUTE ? now : null);
        dispatch.setArrivedAt(null);
        dispatch.setAssignedTo(null); // bước uỷ quyền chọn cán bộ nhận sau
        dispatch.setRespondedAt(null);
        dispatch.setRejectReason(null);
        dispatch.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(dispatch);

        // Tuyến cũ giữ lại để đối chiếu, chỉ đánh dấu đã thay
        incidentRouteRepository.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(saved.getId())
                .ifPresent(old -> {
                    old.setSupersededAt(now);
                    incidentRouteRepository.save(old);
                });
        DispatchPlanRequest.Destination dest = request.getDestination();
        DispatchPlanRequest.Route route = request.getRoute() == null ? new DispatchPlanRequest.Route() : request.getRoute();
        IncidentRoute newRoute = incidentRouteRepository.save(IncidentRoute.builder()
                .dispatch(saved)
                .incidentId(incidentId)
                .stationId(station.getId())
                .originLat(station.getLatitude())
                .originLng(station.getLongitude())
                .originAddress(station.getAddress())
                .originSource(RouteOriginSource.STATION)
                .destLat(dest.getLatitude())
                .destLng(dest.getLongitude())
                .destAddress(dest.getAddress())
                .destSource(dest.getSource())
                .distanceM(route.getDistanceM())
                .durationS(route.getDurationS())
                .durationTrafficS(route.getDurationTrafficS())
                .polyline(route.getPolyline())
                .provider(route.getProvider() == null ? RouteProvider.ESTIMATE : route.getProvider())
                .computedBy(me.memberId())
                .computedAt(now)
                .build());

        incident.setDestLatitude(dest.getLatitude());
        incident.setDestLongitude(dest.getLongitude());
        incident.setDestAddress(dest.getAddress());
        incident.setDestSource(dest.getSource());
        incident.setDestUpdatedBy(dest.getSource() == DestinationSource.OFFICER_EDIT ? me.memberId() : null);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);

        log.info("🗺️ [DISPATCH PLAN] Incident {} -> station {} ({}) by {}", incidentId, station.getId(), status, me.email());
        // Màn chờ của người dân tải lại kế hoạch khi nhận sự kiện này
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, newRoute);
    }

    @Transactional(readOnly = true)
    @Override
    public List<OfficerStatusResponse> getCandidates(String incidentId) {
        IncidentDispatch d = heldPlan(incidentId);
        RescueStation st = d.getStation();
        if (st.getLevel() != StationLevel.WARD || st.wardId() == null) return List.of();
        List<UnitMember> officers = unitMemberRepository.findActiveOfficers(st.getAgencyType(), UnitLevel.WARD).stream()
                .filter(m -> st.wardId().equals(m.getAccount().wardId()) && !m.isLocked())
                .toList();
        List<OfficerStatusResponse> list = officerService.describe(officers);
        // Đang được uỷ quyền một ca khác còn mở cũng là bận (giống lúc giao: delegate() trả 409)
        java.util.Map<UUID, IncidentDispatch> assigned = new java.util.HashMap<>();
        activeAssignments(officers.stream().map(UnitMember::getId).toList()).stream()
                .filter(a -> !a.getIncident().getId().equals(incidentId))
                .forEach(a -> assigned.putIfAbsent(a.getAssignedTo().getId(), a));
        for (OfficerStatusResponse o : list) {
            IncidentDispatch other = assigned.get(o.getMemberId());
            if (other == null || o.isBusy()) continue;
            o.setBusy(true);
            o.setHeldIncident(OfficerStatusResponse.HeldIncident.builder()
                    .id(other.getIncident().getId())
                    .status(other.getIncident().getStatus())
                    .statusLabel("Được uỷ quyền: " + other.getStatus().getLabel())
                    .emergencyType(other.getIncident().getEmergencyType())
                    .build());
        }
        return list;
    }

    @Transactional
    @Override
    public DispatchPlanResponse delegate(String incidentId, UUID memberId) {
        IncidentDispatch d = heldPlan(incidentId);
        if (d.getStatus() != DispatchStatus.PENDING_ACCEPT) {
            throw new SosException(HttpStatus.CONFLICT, "Đơn vị đã nhận ca, không đổi người nhận được nữa");
        }
        requireAnswered(d);
        UnitMember target = unitMemberRepository.findById(memberId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy cán bộ"));
        Account unit = target.getAccount();
        RescueStation st = d.getStation();
        if (!target.isActive() || target.isLocked() || !unit.isActive() || unit.getUnitLevel() != UnitLevel.WARD
                || unit.getAgencyType() != st.getAgencyType() || st.wardId() == null || !st.wardId().equals(unit.wardId())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Cán bộ không thuộc đơn vị của trạm đã chọn");
        }
        if (incidentRepository.existsByHandlerIdAndStatusIn(target.getId(), IncidentStatus.ACTIVE)
                || activeAssignments(List.of(target.getId())).stream()
                        .anyMatch(a -> !a.getIncident().getId().equals(incidentId))) {
            throw new SosException(HttpStatus.CONFLICT, target.displayName() + " đang trong ca khác, chọn cán bộ khác");
        }
        LocalDateTime now = LocalDateTime.now();
        d.setAssignedTo(target);
        d.setRespondedAt(null);
        d.setRejectReason(null);
        d.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        log.info("📨 [DELEGATE] Incident {} -> {} of {} ({})", incidentId, target.getFullName(), unit.getEmail(), st.getName());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, currentRoute(saved));
    }

    @Transactional
    @Override
    public DispatchPlanResponse withdraw(String incidentId) {
        IncidentDispatch d = heldPlan(incidentId);
        if (d.getStatus() != DispatchStatus.PENDING_ACCEPT || d.getAssignedTo() == null) {
            throw new SosException(HttpStatus.CONFLICT, "Không có lượt uỷ quyền nào đang chờ trả lời để thu hồi");
        }
        String from = d.getAssignedTo().getFullName();
        d.setAssignedTo(null);
        d.setRespondedAt(null);
        d.setUpdatedAt(LocalDateTime.now());
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        log.info("↩️ [WITHDRAW] Incident {} delegation to {} withdrawn", incidentId, from);
        // Đơn vị xã không còn được uỷ quyền -> notifier gỡ thẻ ca khỏi hàng đợi của họ
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, currentRoute(saved));
    }

    @Transactional
    @Override
    public DispatchPlanResponse accept(String incidentId) {
        return respond(incidentId, DispatchStatus.PENDING_ACCEPT, d -> d.setStatus(DispatchStatus.ACCEPTED), "✅ [ACCEPT]");
    }

    @Transactional
    @Override
    public DispatchPlanResponse reject(String incidentId, String reason) {
        return respond(incidentId, DispatchStatus.PENDING_ACCEPT, d -> {
            d.setStatus(DispatchStatus.REJECTED);
            d.setRejectReason(reason.trim());
        }, "⛔ [REJECT]");
    }

    @Transactional
    @Override
    public DispatchPlanResponse depart(String incidentId) {
        return respond(incidentId, DispatchStatus.ACCEPTED, d -> {
            d.setStatus(DispatchStatus.EN_ROUTE);
            d.setDepartedAt(LocalDateTime.now());
        }, "🚓 [DEPART]");
    }

    @Transactional
    @Override
    public DispatchPlanResponse arrive(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        boolean allowed = d != null && (d.getAssignedTo() != null
                ? IncidentAccess.isAssignee(me, d)
                : me.memberId().equals(incident.getHandlerId()));
        if (!allowed) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ đơn vị đang đi xử lý mới báo tới hiện trường được");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVING || d.getStatus() != DispatchStatus.EN_ROUTE) {
            throw new SosException(HttpStatus.CONFLICT, "Lực lượng chưa ở bước đang di chuyển");
        }
        LocalDateTime now = LocalDateTime.now();
        d.setStatus(DispatchStatus.ARRIVED);
        d.setArrivedAt(now);
        d.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        log.info("📍 [ARRIVED] Incident {} by {}", incidentId, me.email());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, currentRoute(saved));
    }

    @Transactional
    @Override
    public DispatchPlanResponse confirm(String incidentId, String citizenAccessToken) {
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        boolean byCitizen = citizenAccessToken != null && !citizenAccessToken.isBlank();
        LocalDateTime now = LocalDateTime.now();
        String who;
        if (byCitizen) {
            if (!CitizenTokens.matches(incident, citizenAccessToken)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
            }
            requireAtScene(incident, d);
            incident.setCitizenConfirmedAt(now);
            who = "citizen";
        } else {
            OfficerPrincipal me = currentOfficer();
            if (!isOnSite(me, incident, d)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Chỉ đơn vị đang xử lý tại hiện trường mới xác nhận được");
            }
            requireAtScene(incident, d);
            incident.setOfficerConfirmedAt(now);
            incident.setOfficerConfirmedBy(me.memberId());
            who = me.email();
        }
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        log.info("🤝 [CONFIRM DONE] Incident {} by {}", incidentId, who);
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(d, currentRoute(d));
    }

    @Transactional
    @Override
    public void finish(String incidentId, FinishIncidentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        boolean holder = me.memberId().equals(incident.getHandlerId());
        ReportResult result;
        if (request.isForce()) {
            if (!holder) {
                throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đang giữ ca mới kết thúc bắt buộc được");
            }
            if (incident.getStatus() != IncidentStatus.RESOLVING || d == null
                    || (d.getStatus() != DispatchStatus.EN_ROUTE && d.getStatus() != DispatchStatus.ARRIVED)) {
                throw new SosException(HttpStatus.CONFLICT,
                        "Chỉ kết thúc ca được khi lực lượng đã điều động (đang theo dõi). Chưa điều động thì dùng Huỷ ca");
            }
            if (blank(request.getNote(), 5)) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do kết thúc bắt buộc (ít nhất 5 ký tự)");
            }
            result = ReportResult.FORCED;
        } else {
            if (!holder && !IncidentAccess.isAssignee(me, d)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Chỉ người giữ ca hoặc đơn vị được giao mới lập văn bản được");
            }
            requireAtScene(incident, d);
            boolean skip = request.isSkipConfirm() && demoMode; // nút "Kết thúc ngay (test)"
            if (!skip && (incident.getOfficerConfirmedAt() == null || incident.getCitizenConfirmedAt() == null)) {
                throw new SosException(HttpStatus.CONFLICT, request.isSkipConfirm()
                        ? "Kết thúc ngay chỉ dùng được khi bật chế độ test (DEMO_INCIDENTS=true)"
                        : "Cần cả cán bộ và người dân xác nhận đã xử lý xong");
            }
            if (request.getResult() != ReportResult.RESOLVED && request.getResult() != ReportResult.FALSE_ALARM) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Kết quả phải là Đã xử lý xong hoặc Báo khống");
            }
            if (blank(request.getSummary(), 10)) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng mô tả kết quả xử lý (ít nhất 10 ký tự)");
            }
            result = request.getResult();
        }

        LocalDateTime now = LocalDateTime.now();
        incidentReportRepository.save(IncidentReport.builder()
                .incidentId(incidentId)
                .result(result)
                .summary(trim(request.getSummary()))
                .forces(trim(request.getForces()))
                .note(trim(request.getNote()))
                .createdBy(me.memberId())
                .createdAt(now)
                .build());
        incident.setStatus(IncidentStatus.RESOLVED);
        incident.setUpdatedAt(now);
        incidentRepository.save(incident);
        if (d != null && d.getStatus() != DispatchStatus.REJECTED) {
            d.setStatus(DispatchStatus.DONE);
            d.setUpdatedAt(now);
            incidentDispatchRepository.save(d);
        }
        if (incident.getHandlerId() != null) {
            incidentClaimRepository.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull(incidentId, incident.getHandlerId())
                    .ifPresent(claim -> {
                        claim.setReleasedAt(now);
                        claim.setEndReason(ClaimEndReason.CLOSED);
                        claim.setNote(result.getLabel());
                        incidentClaimRepository.save(claim);
                    });
        }
        log.info("🏁 [FINISH] Incident {} -> RESOLVED ({}) by {}", incidentId, result, me.email());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_REMOVED);
    }

    /** Đơn vị đang có mặt xử lý: cán bộ được giao, hoặc người giữ ca khi không uỷ quyền cho ai. */
    private static boolean isOnSite(OfficerPrincipal me, Incident incident, IncidentDispatch d) {
        if (d == null) return false;
        return d.getAssignedTo() != null ? IncidentAccess.isAssignee(me, d) : me.memberId().equals(incident.getHandlerId());
    }

    private static void requireAtScene(Incident incident, IncidentDispatch d) {
        if (incident.getStatus() != IncidentStatus.RESOLVING || d == null || d.getStatus() != DispatchStatus.ARRIVED) {
            throw new SosException(HttpStatus.CONFLICT, "Lực lượng chưa tới hiện trường");
        }
    }

    private static boolean blank(String text, int min) {
        return text == null || text.trim().length() < min;
    }

    private static String trim(String text) {
        return text == null || text.isBlank() ? null : text.trim();
    }

    /** Đã giao cho một cán bộ mà người đó chưa trả lời: khoá kế hoạch (không đổi trạm / người nhận). */
    private static void requireAnswered(IncidentDispatch d) {
        if (d.getStatus() == DispatchStatus.PENDING_ACCEPT && d.getAssignedTo() != null) {
            throw new SosException(HttpStatus.CONFLICT, "Đang chờ " + d.getAssignedTo().displayName()
                    + " chấp nhận hoặc từ chối, chưa thay đổi được điều động");
        }
    }

    /** Thao tác của cán bộ được giao: đúng người, đúng trạng thái, ca còn đang điều động. */
    private DispatchPlanResponse respond(String incidentId, DispatchStatus from,
                                         java.util.function.Consumer<IncidentDispatch> change, String logTag) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        if (d == null || !IncidentAccess.isAssignee(me, d)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Ca này không được giao cho bạn");
        }
        if (incident.getStatus() != IncidentStatus.RESOLVING || d.getStatus() != from) {
            throw new SosException(HttpStatus.CONFLICT, "Trạng thái điều động đã thay đổi, tải lại hàng đợi");
        }
        LocalDateTime now = LocalDateTime.now();
        if (from == DispatchStatus.PENDING_ACCEPT) d.setRespondedAt(now);
        change.accept(d);
        d.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        log.info("{} Incident {} by {} -> {}", logTag, incidentId, me.email(), saved.getStatus());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, currentRoute(saved));
    }

    /** Lượt chủ trì đang hoạt động của ca do người gọi giữ (bước 2 của bên giao). */
    private IncidentDispatch heldPlan(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        if (!me.memberId().equals(incident.getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đang giữ ca mới uỷ quyền được");
        }
        return currentPrimary(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.CONFLICT, "Chưa chọn trạm xuất phát"));
    }

    private List<IncidentDispatch> activeAssignments(List<UUID> accountIds) {
        if (accountIds.isEmpty()) return List.of();
        return incidentDispatchRepository.findActiveAssignments(accountIds, DispatchStatus.DELEGATED, IncidentStatus.ACTIVE);
    }

    private IncidentDispatch latestPrimary(String incidentId) {
        return incidentDispatchRepository.findFirstByIncident_IdAndRoleOrderByDispatchedAtDesc(incidentId, DispatchRole.PRIMARY)
                .orElse(null);
    }

    private java.util.Optional<IncidentDispatch> currentPrimary(String incidentId) {
        return incidentDispatchRepository.findFirstByIncident_IdAndRoleAndStatusNotOrderByDispatchedAtDesc(
                incidentId, DispatchRole.PRIMARY, DispatchStatus.REJECTED);
    }

    private IncidentRoute currentRoute(IncidentDispatch dispatch) {
        return incidentRouteRepository.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(dispatch.getId())
                .orElse(null);
    }

    private Incident findIncident(String id) {
        return incidentRepository.findById(id)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy vụ việc ID: " + id));
    }

    private OfficerPrincipal currentOfficer() {
        return currentOfficer.require();
    }
}
