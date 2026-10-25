package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.RouteOriginSource;
import com.sosvietnam.model.payload.enums.RouteProvider;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentRouteRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.RouteRecalcService;
import com.sosvietnam.service.RoutingService;
import com.sosvietnam.util.Polylines;
import com.sosvietnam.util.RouteSimulation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class RouteRecalcServiceImpl implements RouteRecalcService {

    /** Số điểm lệch liên tiếp mới tính lại (1 điểm lệch có thể chỉ là GPS nhiễu). */
    static final int OFF_POINTS = 2;
    private static final Duration KEEP = Duration.ofHours(2);

    private final TaskScheduler scheduler;
    private final TransactionTemplate tx;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final RoutingService routingService;
    private final IncidentNotifier notifier;

    @Value("${app.routing.off-route-m:50}")
    private double offRouteM = 50;

    @Value("${app.routing.recalc-cooldown-seconds:20}")
    private long cooldownSeconds = 20;

    /** dispatch id -> số điểm lệch liên tiếp + lần tính lại gần nhất. */
    private final Map<Long, State> states = new ConcurrentHashMap<>();
    /** route id -> polyline đã giải mã (một tuyến không đổi nội dung, chỉ bị thay bằng tuyến mới). */
    private final Map<Long, List<double[]>> paths = new ConcurrentHashMap<>();

    private static final class State {
        int off;
        Instant lastRecalc = Instant.EPOCH;
        Instant seen = Instant.now();
    }

    public RouteRecalcServiceImpl(@Qualifier("incidentTaskScheduler") TaskScheduler scheduler, TransactionTemplate tx,
                                  IncidentDispatchRepository incidentDispatchRepository,
                                  IncidentRouteRepository incidentRouteRepository,
                                  RoutingService routingService, IncidentNotifier notifier) {
        this.scheduler = scheduler;
        this.tx = tx;
        this.incidentDispatchRepository = incidentDispatchRepository;
        this.incidentRouteRepository = incidentRouteRepository;
        this.routingService = routingService;
        this.notifier = notifier;
    }

    @Override
    public void onPosition(Long dispatchId, double lat, double lng) {
        if (dispatchId == null) return;
        scheduler.schedule(() -> {
            try {
                check(dispatchId, new double[]{lat, lng});
            } catch (Exception e) {
                log.warn("🧭 [REROUTE] Lượt {}: {}", dispatchId, e.getMessage());
            }
        }, Instant.now());
    }

    /** Khoá theo lượt: điểm tới dồn dập không tính lại 2 lần cùng lúc. Public cho unit test (gọi đồng bộ). */
    public void check(Long dispatchId, double[] point) {
        State state = states.computeIfAbsent(dispatchId, k -> new State());
        synchronized (state) {
            Instant now = Instant.now();
            state.seen = now;
            states.values().removeIf(s -> s.seen.isBefore(now.minus(KEEP)));

            IncidentRoute route = tx.execute(s -> incidentRouteRepository
                    .findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(dispatchId).orElse(null));
            if (route == null || route.getDestLat() == null || route.getDestLng() == null) return;
            // tuyến ước lượng (không có polyline): coi đường thẳng điểm đi -> điểm đến là tuyến
            List<double[]> path = paths.computeIfAbsent(route.getId(), id -> pathOf(route));
            if (path.size() < 2) return;
            double off = RouteSimulation.distanceToPathM(path, point);
            if (off <= offRouteM) {
                state.off = 0;
                return;
            }
            if (++state.off < OFF_POINTS || state.lastRecalc.plusSeconds(cooldownSeconds).isAfter(now)) return;

            RoutingService.RoadRoute fresh = routingService.route(
                    List.of(point, new double[]{route.getDestLat(), route.getDestLng()}));
            if (fresh == null) return;
            state.off = 0;
            state.lastRecalc = now;
            String incidentId = tx.execute(s -> save(dispatchId, route.getId(), point, fresh));
            if (incidentId != null) {
                log.info("🧭 [REROUTE] Lượt {} (ca {}) lệch {} m: tuyến mới {} m / {} s", dispatchId, incidentId,
                        Math.round(off), fresh.distanceM(), fresh.durationS());
                notifier.notify(incidentId, IncidentEvent.INCIDENT_UPDATED);
            }
        }
    }

    private static List<double[]> pathOf(IncidentRoute route) {
        List<double[]> decoded = route.getPolyline() == null ? List.of() : Polylines.decode(route.getPolyline());
        if (decoded.size() >= 2 || route.getOriginLat() == null || route.getOriginLng() == null) return decoded;
        return List.of(new double[]{route.getOriginLat(), route.getOriginLng()},
                new double[]{route.getDestLat(), route.getDestLng()});
    }

    /** Tuyến mới từ vị trí xe; null nếu lượt không còn trên đường hoặc tuyến đã bị thay trong lúc tính. */
    private String save(Long dispatchId, Long oldRouteId, double[] point, RoutingService.RoadRoute fresh) {
        boolean enRoute = incidentDispatchRepository.findById(dispatchId)
                .map(d -> d.getStatus() == DispatchStatus.EN_ROUTE).orElse(false);
        IncidentRoute old = incidentRouteRepository.findById(oldRouteId).orElse(null);
        if (!enRoute || old == null || old.getSupersededAt() != null) return null;
        LocalDateTime now = LocalDateTime.now();
        old.setSupersededAt(now);
        incidentRouteRepository.save(old);
        incidentRouteRepository.save(IncidentRoute.builder()
                .dispatch(old.getDispatch())
                .incidentId(old.getIncidentId())
                .stationId(old.getStationId())
                .originLat(point[0])
                .originLng(point[1])
                .originSource(RouteOriginSource.VEHICLE)
                .destLat(old.getDestLat())
                .destLng(old.getDestLng())
                .destAddress(old.getDestAddress())
                .destSource(old.getDestSource())
                .distanceM(fresh.distanceM())
                .durationS(fresh.durationS())
                .polyline(fresh.polyline())
                .provider(RouteProvider.OSRM)
                .computedAt(now)
                .build());
        return old.getIncidentId();
    }
}
