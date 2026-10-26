package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.payload.response.RoadSnapResponse;
import com.sosvietnam.service.RoadSnapService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Điểm xe dừng cho vị trí SOS, kiểu Grab: dân trong hẻm thì xe dừng ở đường lớn gần nhất.
 * OSRM nearest (OpenStreetMap, máy chủ {@code app.road-snap.osrm-url}) trả tới 50 điểm gần nhất trên các đoạn đường
 * ô tô đi được, mỗi điểm kèm tên đường; chọn điểm gần nhất trên đường CÓ TÊN và không phải hẻm / kiệt / ngõ / ngách
 * trong bán kính {@code app.road-snap.radius-m} (MAIN_ROAD). Không có thì điểm gần nhất xe vào được (NEAREST_ROAD),
 * OSRM lỗi thì NONE (giữ điểm của dân). Cán bộ luôn ghim lại được trên bản đồ.
 * Không dùng Overpass (lọc đúng loại đường nhưng máy chủ công cộng hay quá tải / treo, 25/10/2026). Google cũng không
 * có API chọn "đường lớn": Routes API chỉ kéo điểm ra đoạn đường xe đi được gần nhất như NEAREST_ROAD.
 * Kết quả cache 30 phút theo toạ độ làm tròn 1e-4 (~11 m).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RoadSnapServiceImpl implements RoadSnapService {

    /** Tên đường nhỏ ở VN: Hẻm 51 Nguyễn Trãi, Kiệt 12 Lê Lợi, Ngõ 35 Phố Tây Sơn, Ngách 4... */
    static final Pattern ALLEY = Pattern.compile("^(hẻm|hẽm|kiệt|ngõ|ngách)(\\s|\\d|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final int CANDIDATES = 50;
    private static final Duration CACHE_TTL = Duration.ofMinutes(30);
    private static final int MAX_CACHE_ENTRIES = 2000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    @Value("${app.road-snap.osrm-url:https://router.project-osrm.org}")
    private String osrmUrl;

    @Value("${app.road-snap.radius-m:300}")
    private int radiusM;

    @Value("${app.road-snap.timeout-seconds:4}")
    private int timeoutSeconds;

    private record Cached(RoadSnapResponse value, Instant at) {
    }

    @Override
    public RoadSnapResponse snap(double lat, double lng) {
        String key = String.format(Locale.ROOT, "%.4f,%.4f", lat, lng);
        Cached hit = cache.get(key);
        if (hit != null && hit.at().plus(CACHE_TTL).isAfter(Instant.now())) return hit.value();

        JsonNode waypoints = nearest(lat, lng);
        RoadSnapResponse result = waypoints == null ? null : pick(waypoints, radiusM);
        if (result == null) {
            return RoadSnapResponse.builder().latitude(lat).longitude(lng).distanceM(0).source("NONE").build();
        }
        if (cache.size() >= MAX_CACHE_ENTRIES) cache.clear();
        cache.put(key, new Cached(result, Instant.now()));
        return result;
    }

    private JsonNode nearest(double lat, double lng) {
        try {
            String url = String.format(Locale.ROOT, "%s/nearest/v1/driving/%.6f,%.6f?number=%d", osrmUrl, lng, lat, CANDIDATES);
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("User-Agent", "sos-vietnam/1.0 (sosvie.site)")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("🛣️ [ROAD SNAP] OSRM trả {}", response.statusCode());
                return null;
            }
            return objectMapper.readTree(response.body()).path("waypoints");
        } catch (Exception e) {
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
            log.warn("🛣️ [ROAD SNAP] OSRM lỗi: {}", e.getMessage());
            return null;
        }
    }

    /**
     * OSRM trả điểm theo thứ tự gần dần: điểm đầu tiên trên đường có tên, không phải hẻm, trong bán kính -> MAIN_ROAD;
     * không có -> điểm gần nhất (NEAREST_ROAD); danh sách rỗng -> null.
     */
    static RoadSnapResponse pick(JsonNode waypoints, int radiusM) {
        if (waypoints == null || !waypoints.isArray() || waypoints.isEmpty()) return null;
        for (JsonNode w : waypoints) {
            String name = w.path("name").asText("").trim();
            if (w.path("distance").asDouble() > radiusM) break;
            if (!name.isEmpty() && !ALLEY.matcher(name).find()) return of(w, name, "MAIN_ROAD");
        }
        JsonNode first = waypoints.get(0);
        String name = first.path("name").asText("").trim();
        return of(first, name.isEmpty() ? null : name, "NEAREST_ROAD");
    }

    private static RoadSnapResponse of(JsonNode w, String name, String source) {
        return RoadSnapResponse.builder()
                .longitude(w.path("location").get(0).asDouble())
                .latitude(w.path("location").get(1).asDouble())
                .roadName(name)
                .distanceM((int) Math.round(w.path("distance").asDouble()))
                .source(source)
                .build();
    }
}
