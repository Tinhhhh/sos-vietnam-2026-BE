package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.WeatherResponse;
import com.sosvietnam.service.WeatherService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
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

/**
 * Gọi Open-Meteo lấy thời tiết hiện tại, cache 10 phút theo toạ độ làm tròn 0.01° (~1 km).
 * Open-Meteo lỗi thì trả 503 để FE hiện "chưa có dữ liệu", không bịa số như fallback của bản gốc.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WeatherServiceImpl implements WeatherService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final int MAX_CACHE_ENTRIES = 1000;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(4))
            .build();
    private final Map<String, CachedWeather> cache = new ConcurrentHashMap<>();

    @Value("${app.weather.source-url:https://api.open-meteo.com}")
    private String weatherSourceUrl;

    private record CachedWeather(WeatherResponse weather, Instant fetchedAt) {
    }

    @Override
    public WeatherResponse getCurrentWeather(double lat, double lng) {
        if (lat < -90 || lat > 90 || lng < -180 || lng > 180) {
            throw new SosException("Toạ độ không hợp lệ");
        }
        String key = String.format(Locale.ROOT, "%.2f_%.2f", lat, lng);
        CachedWeather cached = cache.get(key);
        if (cached != null && cached.fetchedAt().plus(CACHE_TTL).isAfter(Instant.now())) {
            return cached.weather();
        }

        WeatherResponse weather = fetchFromOpenMeteo(lat, lng);
        if (cache.size() >= MAX_CACHE_ENTRIES) {
            cache.clear();
        }
        cache.put(key, new CachedWeather(weather, Instant.now()));
        return weather;
    }

    private WeatherResponse fetchFromOpenMeteo(double lat, double lng) {
        String url = String.format(Locale.ROOT,
                "%s/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,is_day,weather_code,wind_speed_10m"
                        + "&timezone=Asia%%2FBangkok", weatherSourceUrl, lat, lng);
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Open-Meteo HTTP " + response.statusCode());
            }

            JsonNode current = objectMapper.readTree(response.body()).path("current");
            if (!current.has("temperature_2m")) {
                throw new IllegalStateException("Open-Meteo response has no current weather");
            }
            boolean isDay = current.path("is_day").asInt(1) != 0;
            return WeatherResponse.builder()
                    .temperature(Math.round(current.path("temperature_2m").asDouble() * 10) / 10.0)
                    .description(describeWmoCode(current.path("weather_code").asInt(0), isDay))
                    .isDay(isDay)
                    .windSpeed(current.path("wind_speed_10m").asDouble())
                    .build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SosException(HttpStatus.SERVICE_UNAVAILABLE, "Không lấy được dữ liệu thời tiết");
        } catch (Exception e) {
            log.warn("⚠️ [WEATHER] Open-Meteo failed for {},{}: {}", lat, lng, e.getMessage());
            throw new SosException(HttpStatus.SERVICE_UNAVAILABLE, "Không lấy được dữ liệu thời tiết");
        }
    }

    /** Mã thời tiết WMO -> mô tả tiếng Việt (giữ đúng câu chữ interpretWmoWeather của server.js). */
    static String describeWmoCode(int code, boolean isDay) {
        if (code == 0) return isDay ? "Trời quang đãng" : "Đêm quang mây";
        if (code == 1) return isDay ? "Nắng nhẹ / Ít mây" : "Đêm ít mây";
        if (code == 2) return isDay ? "Có mây rải rác" : "Đêm mây rải rác";
        if (code == 3) return isDay ? "Trời nhiều mây" : "Đêm nhiều mây";
        if (code == 45 || code == 48) return "Có sương mù";
        if (code >= 51 && code <= 55) return "Mưa phùn nhẹ";
        if (code >= 61 && code <= 65) return "Mưa rào";
        if (code >= 80 && code <= 82) return "Mưa rào nặng hạt";
        if (code >= 95) return "Mưa dông sấm sét";
        return isDay ? "Thời tiết tốt" : "Đêm mát dịu";
    }
}
