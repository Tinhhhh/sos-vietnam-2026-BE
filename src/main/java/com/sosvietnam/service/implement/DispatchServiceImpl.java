package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.*;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.DispatchPlanRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.model.payload.response.FieldCandidateResponse;
import com.sosvietnam.repository.*;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.DispatchService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.OfficerService;
import com.sosvietnam.service.RoutingService;
import com.sosvietnam.model.entity.IncidentReport;
import com.sosvietnam.model.payload.request.FinishIncidentRequest;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import java.util.List;
import java.util.UUID;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.IncidentAccess;
import com.sosvietnam.util.Polylines;
import com.sosvietnam.util.RouteSimulation;
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
    private final com.sosvietnam.service.DispatchTrackingService dispatchTrackingService;
    private final com.sosvietnam.service.DispatchSimulationService dispatchSimulationService;
    private final com.sosvietnam.service.RoutingService routingService;
    private final com.sosvietnam.service.SmsService smsService;

    // địa chỉ trang web (FE) ghép vào link trong SMS: https://sosvie.site/f/{token}
    @org.springframework.beans.factory.annotation.Value("${app.public-url:http://localhost:5173}")
    private String publicUrl = "http://localhost:5173";


    @Transactional(readOnly = true)
    @Override
    public DispatchPlanResponse getPlan(String incidentId, String citizenAccessToken) {
        Incident incident = findIncident(incidentId);
        boolean citizen = citizenAccessToken != null && !citizenAccessToken.isBlank();
        if (citizen) {
            if (!CitizenTokens.matches(incident, citizenAccessToken)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
            }
        } else {
            requireViewer(incident);
        }
        return planView(incident, latestPrimary(incidentId), !citizen);
    }

    @Transactional(readOnly = true)
    @Override
    public DispatchPlanResponse startSimulation(String incidentId) {
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = requireSimulator(incident);
        if (d == null || d.getStatus() != DispatchStatus.EN_ROUTE) {
            throw new SosException(HttpStatus.CONFLICT, "Lực lượng chưa trên đường tới hiện trường");
        }
        String badge = badgeOfTracker(d);
        if (badge == null) {
            throw new SosException(HttpStatus.CONFLICT, "Người đi hiện trường chưa có số hiệu (mã thiết bị GPS)");
        }
        List<double[]> path = simulationPath(incident, d);
        if (path.size() < 2) {
            throw new SosException(HttpStatus.CONFLICT, "Chưa có toạ độ điểm xuất phát / điểm đến");
        }
        dispatchSimulationService.start(d.getId(), badge,
                RouteSimulation.steps(path, dispatchSimulationService.stepMeters(), SIMULATION_STOP_BEFORE_M));
        log.info("🧪 [SIMULATE] {} bắt đầu mô phỏng ca {}", currentOfficer().email(), incidentId);
        return planView(incident, d, true);
    }

    @Transactional(readOnly = true)
    @Override
    public DispatchPlanResponse stopSimulation(String incidentId) {
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = requireSimulator(incident);
        if (d != null) dispatchSimulationService.stop(d.getId());
        return planView(incident, d, true);
    }

    @Transactional(readOnly = true)
    @Override
    public DispatchPlanResponse detourSimulation(String incidentId) {
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = requireSimulator(incident);
        if (d == null || d.getStatus() != DispatchStatus.EN_ROUTE) {
            throw new SosException(HttpStatus.CONFLICT, "Lực lượng chưa trên đường tới hiện trường");
        }
        String badge = badgeOfTracker(d);
        if (badge == null) {
            throw new SosException(HttpStatus.CONFLICT, "Người đi hiện trường chưa có số hiệu (mã thiết bị GPS)");
        }
        List<double[]> path = simulationPath(incident, d);
        if (path.size() < 2) {
            throw new SosException(HttpStatus.CONFLICT, "Chưa có toạ độ điểm xuất phát / điểm đến");
        }
        // từ chấm xe hiện tại (chưa có thì từ đầu tuyến), đi thẳng 150 m rồi rẽ phải 350 m, sau đó mới về hiện trường
        DispatchPlanResponse.LivePosition last = dispatchTrackingService.lastPosition(d.getId());
        double[] from = last != null ? new double[]{last.getLatitude(), last.getLongitude()} : path.get(0);
        double heading = last != null && last.getHeading() != null ? last.getHeading()
                : RouteSimulation.bearing(path.get(0), path.get(1));
        double[] turn = RouteSimulation.offset(RouteSimulation.offset(from, heading, DETOUR_AHEAD_M),
                heading + 90, DETOUR_SIDE_M);
        RoutingService.RoadRoute detour = routingService.route(List.of(from, turn, path.get(path.size() - 1)));
        if (detour == null || detour.path().size() < 2) {
            throw new SosException(HttpStatus.SERVICE_UNAVAILABLE, "Không tính được đường rẽ (máy chủ chỉ đường lỗi), thử lại sau");
        }
        dispatchSimulationService.start(d.getId(), badge,
                RouteSimulation.steps(detour.path(), dispatchSimulationService.stepMeters(), SIMULATION_STOP_BEFORE_M));
        log.info("🧪 [SIMULATE] {} cho xe ca {} rẽ đường khác ({} m)", currentOfficer().email(), incidentId, detour.distanceM());
        return planView(incident, d, true);
    }

    private static final double DETOUR_AHEAD_M = 150;
    private static final double DETOUR_SIDE_M = 350;

    /** Xe giả dừng cách hiện trường 30 m: người vẫn phải bấm "Tới nơi". */
    private static final double SIMULATION_STOP_BEFORE_M = 30;

    /** Cán bộ xem được ca (hoặc đơn vị được uỷ quyền); trả lượt chủ trì mới nhất. */
    private IncidentDispatch requireViewer(Incident incident) {
        OfficerPrincipal me = currentOfficer();
        IncidentDispatch latest = latestPrimary(incident.getId());
        if (!IncidentAccess.canView(me, incident) && !IncidentAccess.isDelegatedUnit(me, latest)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Ca này không thuộc phạm vi của bạn");
        }
        return latest;
    }

    /**
     * Chỉ người của đơn vị đang đi mô phỏng được: lượt uỷ quyền thì cán bộ được giao, đơn vị tự đi thì người giữ ca.
     * Cán bộ tỉnh đã uỷ quyền cho xã chỉ còn theo dõi.
     */
    private IncidentDispatch requireSimulator(Incident incident) {
        IncidentDispatch d = requireViewer(incident);
        if (d != null && !isGoer(currentOfficer(), incident, d)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ đơn vị đang đi hiện trường mới mô phỏng được");
        }
        return d;
    }

    private static boolean isGoer(OfficerPrincipal me, Incident incident, IncidentDispatch d) {
        if (me == null) return false;
        java.util.UUID goer = d.getAssignedTo() != null ? d.getAssignedTo().getId() : incident.getHandlerId();
        return me.memberId().equals(goer);
    }

    private DispatchPlanResponse planView(Incident incident, IncidentDispatch latest, boolean officer) {
        if (latest == null) return null;
        DispatchPlanResponse plan = DispatchPlanResponse.of(latest, currentRoute(latest));
        boolean enRoute = latest.getStatus() == DispatchStatus.EN_ROUTE;
        // vị trí GPS người đi (Traccar, RAM): FE hỏi lại mỗi 5 giây khi đang trên đường; đã đến / xong thì giữ
        // điểm cuối cùng để bản đồ vẫn hiện chấm xe ở hiện trường
        if (enRoute || latest.getStatus() == DispatchStatus.ARRIVED || latest.getStatus() == DispatchStatus.DONE) {
            plan.setLastPosition(dispatchTrackingService.lastPosition(latest.getId()));
        }
        if (officer) {
            plan.setFieldLinkIssuedAt(latest.getFieldTokenIssuedAt() == null ? null
                    : latest.getFieldTokenIssuedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime());
            plan.setSimulation(DispatchPlanResponse.Simulation.builder()
                    .available(enRoute && badgeOfTracker(latest) != null
                            && isGoer(currentOfficer.orNull(), incident, latest))
                    .running(dispatchSimulationService.running(latest.getId()))
                    .build());
        }
        return plan;
    }


    /** Số hiệu người phụ trách hiện trường; null nếu chưa chọn / chưa có số hiệu. */
    private static String badgeOfTracker(IncidentDispatch d) {
        UnitMember m = d.getFieldMember();
        String badge = m == null ? null : m.getBadgeNumber();
        return badge == null || badge.isBlank() ? null : badge.trim();
    }

    /** Tuyến đã tính (polyline); không có thì đường thẳng trạm -> điểm đến. */
    private List<double[]> simulationPath(Incident incident, IncidentDispatch d) {
        IncidentRoute route = currentRoute(d);
        List<double[]> path = route == null ? List.of() : Polylines.decode(route.getPolyline());
        if (path.size() >= 2) return path;
        Double fromLat = route != null && route.getOriginLat() != null ? route.getOriginLat() : d.getStation().getLatitude();
        Double fromLng = route != null && route.getOriginLng() != null ? route.getOriginLng() : d.getStation().getLongitude();
        Double toLat = route != null && route.getDestLat() != null ? route.getDestLat()
                : incident.getDestLatitude() != null ? incident.getDestLatitude() : incident.getLatitude();
        Double toLng = route != null && route.getDestLng() != null ? route.getDestLng()
                : incident.getDestLongitude() != null ? incident.getDestLongitude() : incident.getLongitude();
        if (fromLat == null || fromLng == null || toLat == null || toLng == null) return List.of();
        return List.of(new double[]{fromLat, fromLng}, new double[]{toLat, toLng});
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

        // Đơn vị mình tự đi: bước 2 "Điều động" chọn người phụ trách hiện trường rồi mới xuất phát (/depart).
        // Đang trên đường mà đổi điểm xuất phát / điểm đến thì vẫn đi tiếp với người cũ.
        IncidentDispatch current = currentPrimary(incidentId).orElse(null);
        boolean keepTrip = !request.isDelegate() && current != null && current.getAssignedTo() == null
                && current.getStatus() == DispatchStatus.EN_ROUTE && current.getFieldMember() != null;
        UnitMember field = keepTrip ? current.getFieldMember() : null;
        if (!request.isDelegate() && request.getFieldMemberId() != null) {
            field = requireFieldMember(me, request.getFieldMemberId(), incidentId);
        }
        if (field != null) incident.setFieldMemberId(field.getId());

        LocalDateTime now = LocalDateTime.now();
        DispatchStatus status = request.isDelegate() ? DispatchStatus.PENDING_ACCEPT
                : field != null ? DispatchStatus.EN_ROUTE : DispatchStatus.ACCEPTED;
        LocalDateTime departedAt = keepTrip ? current.getDepartedAt() : now;
        IncidentDispatch dispatch = current != null ? current : IncidentDispatch.builder()
                .incident(incident)
                .role(DispatchRole.PRIMARY)
                .build();
        dispatch.setStation(station);
        dispatch.setStatus(status);
        dispatch.setDispatchedBy(unitMemberRepository.getReferenceById(me.memberId()));
        dispatch.setUnitLevel(me.level());
        dispatch.setDispatchedAt(now);
        dispatch.setDepartedAt(status == DispatchStatus.EN_ROUTE ? departedAt : null);
        dispatch.setArrivedAt(null);
        dispatch.setAssignedTo(null); // bước uỷ quyền chọn cán bộ nhận sau
        dispatch.setFieldMember(field);
        dispatch.setRespondedAt(null);
        dispatch.setRejectReason(null);
        dispatch.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(dispatch);
        if (saved.getId() != null) dispatchSimulationService.stop(saved.getId()); // tuyến / người đi có thể đã đổi

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
        incident.setDestUpdatedBy(dest.getSource() == DestinationSource.CITIZEN_GPS ? null : me.memberId());
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
        // chỉ đơn vị xã của trạm (trước đây tải mọi cán bộ cấp xã cả nước rồi lọc: ~10 nghìn dòng)
        List<UnitMember> officers = unitMemberRepository.findActiveOfficersOfUnit(st.getAgencyType(), UnitLevel.WARD,
                        st.getProvince() == null ? null : st.getProvince().getCode(), st.wardId()).stream()
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
        d.setRejectReason(null);
        d.setUpdatedAt(now);
        if (appointsFieldOfficer(st)) {
            // PCCC / CSGT: người được chỉ định cũng là người phụ trách hiện trường (cầm GPS), đi luôn không chờ nhận
            d.setFieldMember(requireFieldReady(target, incidentId));
            d.setStatus(DispatchStatus.EN_ROUTE);
            d.setRespondedAt(now);
            d.setDepartedAt(now);
        } else {
            d.setRespondedAt(null);
        }
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        log.info("📨 [DELEGATE] Incident {} -> {} of {} ({}) {}", incidentId, target.getFullName(), unit.getEmail(),
                st.getName(), saved.getStatus());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(saved, currentRoute(saved));
    }

    /** PCCC / CSGT: chỉ định một người của đơn vị xuất phát là đi luôn (không có bước Chấp nhận). */
    private static boolean appointsFieldOfficer(RescueStation station) {
        return station.getAgencyType() == AgencyType.FIRE || station.getAgencyType() == AgencyType.CSGT;
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
    public DispatchPlanResponse depart(String incidentId, UUID fieldMemberId) {
        OfficerPrincipal me = currentOfficer();
        IncidentDispatch current = latestPrimary(incidentId);
        if (current != null && current.getAssignedTo() == null) {
            // đơn vị của người giữ ca tự đi: bước 2 "Điều động" -> chọn người phụ trách -> "Lập tức điều động"
            Incident incident = findIncident(incidentId);
            if (!me.memberId().equals(incident.getHandlerId())) {
                throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ đang giữ ca mới điều động được");
            }
            if (incident.getStatus() != IncidentStatus.RESOLVING || current.getStatus() != DispatchStatus.ACCEPTED) {
                throw new SosException(HttpStatus.CONFLICT, "Trạng thái điều động đã thay đổi, tải lại cửa sổ");
            }
            LocalDateTime now = LocalDateTime.now();
            current.setFieldMember(requireFieldMember(me, fieldMemberId, incidentId));
            current.setStatus(DispatchStatus.EN_ROUTE);
            current.setDepartedAt(now);
            current.setUpdatedAt(now);
            incident.setFieldMemberId(fieldMemberId);
            incidentRepository.save(incident);
            IncidentDispatch saved = incidentDispatchRepository.save(current);
            log.info("🚓 [DEPART] Incident {} by {} (own unit), field {}", incidentId, me.email(),
                    saved.getFieldMember().getFullName());
            notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
            return planView(incident, saved, true);
        }
        return respond(incidentId, DispatchStatus.ACCEPTED, d -> {
            d.setFieldMember(requireFieldMember(me, fieldMemberId, incidentId));
            d.setStatus(DispatchStatus.EN_ROUTE);
            d.setDepartedAt(LocalDateTime.now());
        }, "🚓 [DEPART]");
    }

    @Transactional(readOnly = true)
    @Override
    public List<FieldCandidateResponse> getFieldCandidates(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        // người giữ ca (đang chọn lại trạm của mình ở bước 1) hoặc cán bộ được uỷ quyền: mỗi người thấy đơn vị mình
        if (!me.memberId().equals(findIncident(incidentId).getHandlerId())
                && !IncidentAccess.isAssignee(me, latestPrimary(incidentId))) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ người nhận ca của đơn vị đi xử lý mới chọn người phụ trách được");
        }
        List<UnitMember> members = unitMemberRepository.findByAccount_IdAndActiveTrueOrderByFullNameAsc(me.unitAccountId());
        java.util.Map<UUID, String> onTrip = new java.util.HashMap<>();
        activeFieldTrips(members.stream().map(UnitMember::getId).toList()).stream()
                .filter(t -> !t.getIncident().getId().equals(incidentId))
                .forEach(t -> onTrip.putIfAbsent(t.getFieldMember().getId(), t.getIncident().getId()));
        java.util.Set<UUID> online = notifier.onlineMemberIds();
        return members.stream().map(m -> {
            String reason = onTrip.containsKey(m.getId()) ? "Đang phụ trách ca " + onTrip.get(m.getId())
                    : blank(m.getBadgeNumber(), 1) ? "Chưa có số hiệu (mã thiết bị GPS)" : null;
            return FieldCandidateResponse.builder()
                    .memberId(m.getId())
                    .fullName(m.displayName())
                    .badgeNumber(m.getBadgeNumber())
                    .position(m.getPosition())
                    .unitRole(m.getUnitRole())
                    .phone(m.getPhone())
                    .online(online.contains(m.getId()))
                    .me(m.getId().equals(me.memberId()))
                    .selectable(reason == null)
                    .unavailableReason(reason)
                    .build();
        }).toList();
    }

    @Transactional(readOnly = true)
    @Override
    public void checkFieldMember(String incidentId, UUID fieldMemberId) {
        OfficerPrincipal me = currentOfficer();
        if (!me.memberId().equals(findIncident(incidentId).getHandlerId())) {
            throw new SosException(HttpStatus.FORBIDDEN, "Bạn không giữ ca này");
        }
        requireFieldMember(me, fieldMemberId, incidentId);
    }

    @Transactional
    @Override
    public DispatchPlanResponse changeFieldMember(String incidentId, UUID fieldMemberId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        requireFieldChooser(me, incident, d);
        if (incident.getStatus() != IncidentStatus.RESOLVING || d == null || d.getStatus() != DispatchStatus.EN_ROUTE) {
            throw new SosException(HttpStatus.CONFLICT, "Chỉ đổi người phụ trách được khi lực lượng đang trên đường");
        }
        d.setFieldMember(requireFieldMember(me, fieldMemberId, incidentId));
        d.setFieldTokenHash(null); // link cũ là của người cũ
        d.setFieldTokenIssuedAt(null);
        d.setUpdatedAt(LocalDateTime.now());
        if (d.getAssignedTo() == null) {
            incident.setFieldMemberId(fieldMemberId);
            incidentRepository.save(incident);
        }
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        dispatchSimulationService.stop(saved.getId()); // điện thoại GPS đã đổi người
        log.info("🎒 [FIELD MEMBER] Incident {} -> {} by {}", incidentId, saved.getFieldMember().getFullName(), me.email());
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return planView(incident, saved, true);
    }

    /**
     * Người chọn người phụ trách hiện trường = người nhận ca của đơn vị đi: cán bộ được uỷ quyền nếu có,
     * không thì người đang giữ ca (chính đơn vị mình đi).
     */
    private static void requireFieldChooser(OfficerPrincipal me, Incident incident, IncidentDispatch d) {
        boolean delegated = d != null && d.getAssignedTo() != null && d.getStatus() != DispatchStatus.REJECTED;
        boolean allowed = delegated ? IncidentAccess.isAssignee(me, d) : me.memberId().equals(incident.getHandlerId());
        if (!allowed) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ người nhận ca của đơn vị đi xử lý mới chọn người phụ trách được");
        }
    }

    /** Cán bộ đang hoạt động của đơn vị người chọn, có số hiệu, không phụ trách lượt khác còn mở. Không cần trực tuyến. */
    private UnitMember requireFieldMember(OfficerPrincipal me, UUID memberId, String incidentId) {
        UnitMember m = unitMemberRepository.findById(memberId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy cán bộ"));
        if (!m.isActive() || !m.getAccount().getId().equals(me.unitAccountId())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Người phụ trách phải là cán bộ của đơn vị bạn");
        }
        return requireFieldReady(m, incidentId);
    }

    /** Có số hiệu (mã thiết bị GPS) và không đang phụ trách lượt khác của ca còn mở. */
    private UnitMember requireFieldReady(UnitMember m, String incidentId) {
        if (blank(m.getBadgeNumber(), 1)) {
            throw new SosException(HttpStatus.BAD_REQUEST, m.displayName() + " chưa có số hiệu (mã thiết bị GPS)");
        }
        activeFieldTrips(List.of(m.getId())).stream()
                .filter(t -> !t.getIncident().getId().equals(incidentId))
                .findFirst()
                .ifPresent(t -> {
                    throw new SosException(HttpStatus.CONFLICT,
                            m.displayName() + " đang phụ trách ca " + t.getIncident().getId() + ", chọn người khác");
                });
        return m;
    }

    private List<IncidentDispatch> activeFieldTrips(List<UUID> memberIds) {
        if (memberIds.isEmpty()) return List.of();
        return incidentDispatchRepository.findActiveFieldTrips(memberIds,
                java.util.EnumSet.of(DispatchStatus.EN_ROUTE, DispatchStatus.ARRIVED), IncidentStatus.ACTIVE);
    }

    @Transactional
    @Override
    public DispatchPlanResponse arrive(String incidentId, String citizenAccessToken) {
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        String who;
        if (citizenAccessToken != null && !citizenAccessToken.isBlank()) {
            if (!CitizenTokens.matches(incident, citizenAccessToken)) {
                throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ cho sự cố này");
            }
            who = "citizen";
        } else {
            OfficerPrincipal me = currentOfficer();
            // một bên bấm "Đã đến" là đủ: đơn vị đi xử lý hoặc chính người phụ trách hiện trường
            boolean allowed = d != null && (isOnSite(me, incident, d)
                    || (d.getFieldMember() != null && me.memberId().equals(d.getFieldMember().getId())));
            if (!allowed) {
                throw new SosException(HttpStatus.FORBIDDEN, "Chỉ đơn vị đang đi xử lý mới báo tới hiện trường được");
            }
            who = me.email();
        }
        return markArrived(incident, d, who, !"citizen".equals(who));
    }

    private DispatchPlanResponse markArrived(Incident incident, IncidentDispatch d, String who, boolean officerView) {
        if (d == null || incident.getStatus() != IncidentStatus.RESOLVING || d.getStatus() != DispatchStatus.EN_ROUTE) {
            throw new SosException(HttpStatus.CONFLICT, "Lực lượng chưa ở bước đang di chuyển");
        }
        LocalDateTime now = LocalDateTime.now();
        d.setStatus(DispatchStatus.ARRIVED);
        d.setArrivedAt(now);
        d.setUpdatedAt(now);
        IncidentDispatch saved = incidentDispatchRepository.save(d);
        dispatchSimulationService.stop(saved.getId()); // xe giả dừng ở điểm cuối, chấm xe vẫn hiện ở đó
        log.info("📍 [ARRIVED] Incident {} by {}", incident.getId(), who);
        notifier.notify(incident.getId(), IncidentEvent.INCIDENT_UPDATED);
        return planView(incident, saved, officerView);
    }

    // ------------------------------------------------------------------ link người phụ trách hiện trường

    @Transactional
    @Override
    public com.sosvietnam.model.payload.response.FieldLinkResponse issueFieldLink(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = findIncident(incidentId);
        IncidentDispatch d = latestPrimary(incidentId);
        boolean allowed = me.memberId().equals(incident.getHandlerId()) || IncidentAccess.isAssignee(me, d);
        if (!allowed) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ người giữ ca hoặc đơn vị được giao mới cấp link được");
        }
        if (d == null || incident.getStatus() != IncidentStatus.RESOLVING || d.getFieldMember() == null
                || (d.getStatus() != DispatchStatus.EN_ROUTE && d.getStatus() != DispatchStatus.ARRIVED)) {
            throw new SosException(HttpStatus.CONFLICT, "Chỉ cấp link khi lực lượng đã xuất phát và có người phụ trách");
        }
        // vừa gửi SMS cho lượt này: chặn bấm liên tục (mỗi lần cấp lại là 1 tin, tốn cước + dễ bị nhà mạng coi là spam)
        java.time.Instant lastSms = fieldSmsSent.get(d.getId());
        long wait = lastSms == null ? 0
                : FIELD_SMS_COOLDOWN_SECONDS - java.time.Duration.between(lastSms, java.time.Instant.now()).getSeconds();
        if (wait > 0) {
            throw new SosException(HttpStatus.TOO_MANY_REQUESTS, "Vừa gửi SMS link, thử lại sau " + wait + " giây");
        }
        byte[] bytes = new byte[24];
        FIELD_TOKEN_RANDOM.nextBytes(bytes);
        String token = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now();
        d.setFieldTokenHash(CitizenTokens.sha256(token));
        d.setFieldTokenIssuedAt(now);
        d.setUpdatedAt(now);
        incidentDispatchRepository.save(d);
        log.info("🔗 [FIELD LINK] Incident {} link for {} issued by {}", incidentId, d.getFieldMember().getFullName(), me.email());
        UnitMember m = d.getFieldMember();
        return com.sosvietnam.model.payload.response.FieldLinkResponse.builder()
                .token(token)
                .path("/f/" + token)
                .issuedAt(now.atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime())
                .fieldMember(DispatchPlanResponse.Officer.builder().memberId(m.getId()).fullName(m.displayName())
                        .phone(m.getPhone()).badgeNumber(m.getBadgeNumber()).build())
                .sms(sendFieldLinkSms(d, incidentId, token))
                .build();
    }

    /**
     * SMS link cho người phụ trách (số trong hồ sơ cán bộ). Không dấu để vừa 1 tin 160 ký tự. Không có số / chưa cấu
     * hình cổng SMS thì không gửi: cán bộ vẫn sao chép link như cũ.
     */
    private com.sosvietnam.model.payload.response.FieldLinkResponse.Sms sendFieldLinkSms(IncidentDispatch d,
                                                                                         String incidentId, String token) {
        String phone = d.getFieldMember().getPhone();
        if (phone == null || phone.isBlank()) {
            return com.sosvietnam.model.payload.response.FieldLinkResponse.Sms.builder()
                    .sent(false).error("Người phụ trách chưa có số điện thoại trong hồ sơ").build();
        }
        String text = "SOS VN: Ca " + incidentId + " - ban la nguoi phu trach hien truong. Mo link: "
                + publicUrl.replaceAll("/+$", "") + "/f/" + token;
        com.sosvietnam.service.SmsService.Result r = smsService.send(phone, text);
        if (r.sent()) fieldSmsSent.put(d.getId(), java.time.Instant.now());
        return com.sosvietnam.model.payload.response.FieldLinkResponse.Sms.builder()
                .sent(r.sent())
                .phoneMasked(com.sosvietnam.model.payload.response.IncidentLookupResponse.maskPhone(phone))
                .error(r.error())
                .build();
    }

    private static final long FIELD_SMS_COOLDOWN_SECONDS = 60;
    // lượt -> lúc gửi SMS link gần nhất (RAM, chỉ đúng 1 instance; mất khi khởi động lại cũng không sao)
    private final java.util.Map<Long, java.time.Instant> fieldSmsSent = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.security.SecureRandom FIELD_TOKEN_RANDOM = new java.security.SecureRandom();

    /** Lượt của mã link: sai / đã cấp lại / đổi người -> 401; ca đã đóng -> 410. */
    private IncidentDispatch requireFieldDispatch(String fieldToken) {
        if (fieldToken == null || fieldToken.isBlank()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Thiếu mã link người phụ trách hiện trường");
        }
        IncidentDispatch d = incidentDispatchRepository.findByFieldTokenHash(CitizenTokens.sha256(fieldToken.trim()))
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED,
                        "Link không còn hiệu lực (đã cấp lại hoặc đổi người phụ trách)"));
        if (!IncidentStatus.ACTIVE.contains(d.getIncident().getStatus()) || d.getStatus() == DispatchStatus.REJECTED) {
            throw new SosException(HttpStatus.GONE, "Ca đã kết thúc");
        }
        return d;
    }

    @Transactional(readOnly = true)
    @Override
    public com.sosvietnam.model.payload.response.FieldPlanResponse getFieldPlan(String fieldToken) {
        IncidentDispatch d = requireFieldDispatch(fieldToken);
        Incident i = d.getIncident();
        String handlerName = i.getHandlerId() == null ? null
                : unitMemberRepository.findById(i.getHandlerId()).map(UnitMember::displayName).orElse(null);
        String address = java.util.stream.Stream.of(i.getAddress(), i.wardName(), i.provinceName())
                .filter(s -> s != null && !s.isBlank()).collect(java.util.stream.Collectors.joining(", "));
        return com.sosvietnam.model.payload.response.FieldPlanResponse.builder()
                .incidentId(i.getId())
                .incidentStatus(i.getStatus())
                .emergencyType(i.getEmergencyType())
                .emergencyTypeLabel(i.getEmergencyType() == null ? null : i.getEmergencyType().getLabel())
                .incidentTag(i.getIncidentTag())
                .description(i.getDescription())
                .citizenName(i.getCitizenName())
                .citizenPhone(i.getPhone())
                .address(address)
                .createdAt(i.getCreatedAt() == null ? null : i.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toOffsetDateTime())
                .handlerName(handlerName)
                .plan(planView(i, d, false))
                .build();
    }

    @Transactional(readOnly = true)
    @Override
    public boolean fieldPosition(String fieldToken, com.sosvietnam.model.payload.request.FieldPositionRequest point) {
        IncidentDispatch d = requireFieldDispatch(fieldToken);
        if (d.getStatus() != DispatchStatus.EN_ROUTE) return false; // chỉ theo dõi khi đang trên đường
        return dispatchTrackingService.acceptBrowser(d.getId(), point);
    }

    @Transactional
    @Override
    public DispatchPlanResponse fieldArrive(String fieldToken) {
        IncidentDispatch d = requireFieldDispatch(fieldToken);
        return markArrived(d.getIncident(), d, "field link", false);
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
            // dân chỉ xác nhận, ca chỉ đóng khi cán bộ kết thúc (cần cả hai bên, trừ kết thúc bắt buộc)
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
        // bên kia (cán bộ đã lập văn bản "Đã xử lý xong") đã xác nhận trước -> đủ hai bên, đóng ca
        if (closeIfBothConfirmed(incident, d, now)) {
            return DispatchPlanResponse.of(d, currentRoute(d));
        }
        notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
        return DispatchPlanResponse.of(d, currentRoute(d));
    }

    /** Cả dân và cán bộ đã xác nhận, cán bộ đã lập văn bản: đóng ca bằng văn bản đó. */
    private boolean closeIfBothConfirmed(Incident incident, IncidentDispatch d, LocalDateTime now) {
        if (incident.getCitizenConfirmedAt() == null || incident.getOfficerConfirmedAt() == null) return false;
        IncidentReport draft = incidentReportRepository.findById(incident.getId()).orElse(null);
        if (draft == null) return false;
        close(incident, d, draft, now);
        log.info("🏁 [FINISH] Incident {} -> RESOLVED ({}) after both sides confirmed", incident.getId(), draft.getResult());
        return true;
    }

    @Transactional
    @Override
    public boolean finish(String incidentId, FinishIncidentRequest request) {
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
            result = request.getResult();
            if (result == ReportResult.TEST) {
                // ca thử nghiệm: đóng ngay ở bất kỳ bước điều động nào, không cần mô tả
                if (incident.getStatus() != IncidentStatus.RESOLVING) {
                    throw new SosException(HttpStatus.CONFLICT, "Ca không còn ở bước điều động");
                }
            } else {
                requireAtScene(incident, d);
                if (result != ReportResult.RESOLVED && result != ReportResult.FALSE_ALARM) {
                    throw new SosException(HttpStatus.BAD_REQUEST, "Kết quả phải là Đã xử lý xong, Báo khống hoặc Ca test");
                }
                if (blank(request.getSummary(), 10)) {
                    throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng mô tả kết quả xử lý (ít nhất 10 ký tự)");
                }
            }
        }

        LocalDateTime now = LocalDateTime.now();
        IncidentReport report = IncidentReport.builder()
                .incidentId(incidentId)
                .result(result)
                .summary(result == ReportResult.TEST && blank(request.getSummary(), 1) ? "Ca test" : trim(request.getSummary()))
                .forces(trim(request.getForces()))
                .note(trim(request.getNote()))
                .createdBy(me.memberId())
                .createdAt(now)
                .build();
        if (result != ReportResult.FORCED) {
            incident.setOfficerConfirmedAt(now);
            incident.setOfficerConfirmedBy(me.memberId());
        }
        // "Đã xử lý xong" cần cả hai bên (thứ tự nào cũng được): dân chưa xác nhận thì ghi văn bản + xác nhận của
        // cán bộ, ca đóng khi dân xác nhận. Báo khống / ca test / kết thúc bắt buộc đóng ngay.
        if (result == ReportResult.RESOLVED && incident.getCitizenConfirmedAt() == null) {
            incidentReportRepository.save(report);
            incident.setUpdatedAt(now);
            incidentRepository.save(incident);
            log.info("📝 [FINISH] Incident {} officer confirmed by {}, waiting for the citizen", incidentId, me.email());
            notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
            return false;
        }
        close(incident, d, report, now);
        log.info("🏁 [FINISH] Incident {} -> RESOLVED ({}) by {}", incidentId, result, me.email());
        return true;
    }

    /** Đóng ca: văn bản kết thúc, ca RESOLVED, lượt DONE, đóng lượt giữ ca, gỡ thẻ khỏi hàng đợi. */
    private void close(Incident incident, IncidentDispatch d, IncidentReport report, LocalDateTime now) {
        String incidentId = incident.getId();
        ReportResult result = report.getResult();
        incidentReportRepository.save(report);
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
