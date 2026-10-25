package com.sosvietnam.service.implement;

import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.service.DispatchSimulationService;
import com.sosvietnam.service.DispatchTrackingService;
import com.sosvietnam.util.RouteSimulation;
import lombok.extern.slf4j.Slf4j;
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
import java.time.OffsetDateTime;
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

    @Value("${app.traccar.simulation.interval-seconds:5}")
    private long intervalSeconds;

    @Value("${app.traccar.simulation.speed-kmh:50}")
    private double speedKmh;

    public DispatchSimulationServiceImpl(@Qualifier("incidentTaskScheduler") TaskScheduler scheduler,
                                         IncidentDispatchRepository incidentDispatchRepository,
                                         DispatchTrackingService dispatchTrackingService) {
        this.scheduler = scheduler;
        this.incidentDispatchRepository = incidentDispatchRepository;
        this.dispatchTrackingService = dispatchTrackingService;
    }

    @Override
    public double stepMeters() {
        return speedKmh / 3.6 * intervalSeconds;
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
                send(badge, steps.get(i));
                if (i == steps.size() - 1) stop(dispatchId);
            } catch (Exception e) {
                log.warn("[SIMULATE] Lượt {}: {}", dispatchId, e.getMessage());
            }
        }, Instant.now(), Duration.ofSeconds(intervalSeconds));
        runs.put(dispatchId, run);
        log.info("🧪 [SIMULATE] Lượt {} chạy {} điểm, {} km/h, qua {}", dispatchId, steps.size(), speedKmh,
                hasTraccar() ? "Traccar" : "thẳng vào BE");
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

    private void send(String badge, RouteSimulation.Step step) throws Exception {
        double speedKnot = speedKmh * KMH_TO_KNOT;
        if (!hasTraccar()) {
            dispatchTrackingService.accept(TraccarPositionRequest.builder()
                    .device(TraccarPositionRequest.Device.builder().uniqueId(badge).build())
                    .position(TraccarPositionRequest.Position.builder()
                            .latitude(step.lat()).longitude(step.lng()).speed(speedKnot).course(step.heading())
                            .accuracy(5.0).fixTime(OffsetDateTime.now().toString()).valid(true).build())
                    .build());
            return;
        }
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
