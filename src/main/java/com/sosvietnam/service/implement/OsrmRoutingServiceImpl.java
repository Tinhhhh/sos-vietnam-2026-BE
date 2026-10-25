package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.service.RoutingService;
import com.sosvietnam.util.Polylines;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** OSRM route/v1/driving (OpenStreetMap); polyline 1e-5 lấy thẳng từ OSRM. */
@Slf4j
@Service
@RequiredArgsConstructor
public class OsrmRoutingServiceImpl implements RoutingService {

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();

    @Value("${app.routing.osrm-url:https://router.project-osrm.org}")
    private String osrmUrl;

    @Value("${app.routing.timeout-seconds:6}")
    private int timeoutSeconds;

    @Override
    public RoadRoute route(List<double[]> points) {
        if (points == null || points.size() < 2) return null;
        String coords = points.stream()
                .map(p -> String.format(Locale.ROOT, "%.6f,%.6f", p[1], p[0]))
                .collect(Collectors.joining(";"));
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(osrmUrl + "/route/v1/driving/" + coords
                            + "?overview=full&geometries=polyline"))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "sos-vietnam/1.0 (sosvie.site)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("🧭 [ROUTING] OSRM trả {}", response.statusCode());
                return null;
            }
            JsonNode route = objectMapper.readTree(response.body()).path("routes").path(0);
            String polyline = route.path("geometry").asText("");
            if (route.isMissingNode() || polyline.isEmpty()) return null;
            return new RoadRoute(polyline, Polylines.decode(polyline),
                    (int) Math.round(route.path("distance").asDouble()),
                    (int) Math.round(route.path("duration").asDouble()));
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("🧭 [ROUTING] OSRM lỗi: {}", e.getMessage());
            return null;
        }
    }
}
