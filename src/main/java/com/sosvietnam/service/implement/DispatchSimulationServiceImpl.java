package com.sosvietnam.service.implement;

import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.service.DemoModeService;
import com.sosvietnam.service.DispatchSimulationService;
import com.sosvietnam.service.DispatchTrackingService;
import com.sosvietnam.util.RouteSimulation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class DispatchSimulationServiceImpl implements DispatchSimulationService {

    private static final double KMH_TO_KNOT = 1 / 1.852;

    private final TaskScheduler scheduler;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final DispatchTrackingService dispatchTrackingService;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<Long, ScheduledFuture<?>> runs = new ConcurrentHashMap<>();

    /** Cổng OsmAnd của Traccar (trên VPS: http://traccar:5055/). Trống = không qua Traccar. */
    @Value("${app.traccar.osmand-url:}")
    private String osmandUrl;

    @Value("${app.traccar.simulation.interval-seconds:3}")
    private long intervalSeconds;

    @Value("${app.traccar.simulation.speed-kmh:50}")
    private double speedKmh;

    // tốc độ chỉnh ở Cài đặt (app_settings). Field không bắt buộc: test dựng tay dùng speedKmh
    @Autowired(required = false)
    private DemoModeService demoModeService;

    @Override
    public double speedKmh() {
        return demoModeService == null ? speedKmh : demoModeService.simulationSpeedKmh();
    }

    public DispatchSimulationServiceImpl(@Qualifier("incidentTaskScheduler") TaskScheduler scheduler,
                                         IncidentDispatchRepository incidentDispatchRepository,
                                         DispatchTrackingService dispatchTrackingService) {
        this.scheduler = scheduler;
        this.incidentDispatchRepository = incidentDispatchRepository;
        this.dispatchTrackingService = dispatchTrackingService;
    }

    @Override
    public double stepMeters() {
        return speedKmh() / 3.6 * intervalSeconds;
    }

    @Override
    public void start(Long dispatchId, String badge, List<RouteSimulation.Step> steps) {
        stop(dispatchId);
        if (steps.isEmpty()) return;
        AtomicInteger next = new AtomicInteger();
        ScheduledFuture<?> run = scheduler.scheduleAtFixedRate(() -> {
            try {
                boolean enRoute = incidentDispatchRepository.findById(dispatchId)
                        .map(d -> d.getStatus() == DispatchStatus.EN_ROUTE).orElse(false);
                int i = next.getAndIncrement();
                if (!enRoute || i >= steps.size()) {
                    stop(dispatchId);
                    return;
                }
                send(dispatchId, badge, steps.get(i));
                if (i == steps.size() - 1) stop(dispatchId);
            } catch (Exception e) {
                log.warn("[SIMULATE] Lượt {}: {}", dispatchId, e.getMessage());
            }
        }, Instant.now(), Duration.ofSeconds(intervalSeconds));
        runs.put(dispatchId, run);
        log.info("🧪 [SIMULATE] Lượt {} chạy {} điểm, {} km/h, nạp thẳng vào BE{}", dispatchId, steps.size(), speedKmh(),
                hasTraccar() ? " + gửi bản sao lên Traccar" : "");
    }

    @Override
    public void stop(Long dispatchId) {
        ScheduledFuture<?> run = runs.remove(dispatchId);
        if (run != null) run.cancel(false);
    }

    @Override
    public boolean running(Long dispatchId) {
        return dispatchId != null && runs.containsKey(dispatchId);
    }

    private boolean hasTraccar() {
        return osmandUrl != null && !osmandUrl.isBlank();
    }

    /**
     * Điểm xe giả nạp thẳng vào BE (không chờ Traccar: Traccar chậm / bỏ thiết bị lạ thì xe đứng im, còn điện thoại
     * thật của người đi lại kéo xe về chỗ nó đứng). Có Traccar thì gửi thêm bản sao để xem trên bản đồ Traccar;
     * bản Traccar dội về BE bị bỏ vì lượt đang mô phỏng.
     */
    private void send(Long dispatchId, String badge, RouteSimulation.Step step) {
        double speedKmh = speedKmh();
        dispatchTrackingService.acceptSimulated(dispatchId, step.lat(), step.lng(), speedKmh, step.heading());
        if (!hasTraccar()) return;
        try {
            mirrorToTraccar(badge, step, speedKmh * KMH_TO_KNOT);
        } catch (Exception e) {
            log.debug("[SIMULATE] Không gửi được bản sao lên Traccar: {}", e.getMessage());
        }
    }

    private void mirrorToTraccar(String badge, RouteSimulation.Step step, double speedKnot) throws Exception {
        // Đúng như app Traccar Client (giao thức OsmAnd): GET ?id=&lat=&lon=&speed=(knot)&bearing=&timestamp=
        String query = String.format(Locale.ROOT, "id=%s&lat=%.6f&lon=%.6f&speed=%.1f&bearing=%.0f&accuracy=5&timestamp=%d",
                URLEncoder.encode(badge, StandardCharsets.UTF_8), step.lat(), step.lng(), speedKnot, step.heading(),
                Instant.now().getEpochSecond());
        String base = osmandUrl.endsWith("/") ? osmandUrl : osmandUrl + "/";
        HttpResponse<Void> res = http.send(HttpRequest.newBuilder(URI.create(base + "?" + query))
                .timeout(Duration.ofSeconds(3)).GET().build(), HttpResponse.BodyHandlers.discarding());
        if (res.statusCode() >= 300) {
            log.warn("[SIMULATE] Traccar trả {} cho số hiệu {}", res.statusCode(), badge);
        }
    }
}
