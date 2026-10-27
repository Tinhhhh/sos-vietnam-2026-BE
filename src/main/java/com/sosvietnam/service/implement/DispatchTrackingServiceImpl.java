package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.service.DispatchTrackingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class DispatchTrackingServiceImpl implements DispatchTrackingService {

    private static final double KNOT_TO_KMH = 1.852;
    /** App Traccar gửi điểm trong khoảng này thì bỏ điểm trình duyệt (app chính xác hơn). */
    private static final Duration APP_PRIORITY = Duration.ofSeconds(20);
    static final String SOURCE_APP = "TRACCAR";
    static final String SOURCE_BROWSER = "BROWSER";
    /** Điểm cũ hơn mức này bị dọn khỏi RAM (lượt đã xong từ lâu). */
    private static final Duration KEEP = Duration.ofHours(2);

    private final IncidentDispatchRepository incidentDispatchRepository;
    private final com.sosvietnam.service.RouteRecalcService routeRecalcService;

    /** dispatch id -> điểm mới nhất. */
    private final Map<Long, DispatchPlanResponse.LivePosition> positions = new ConcurrentHashMap<>();

    @Transactional(readOnly = true)
    @Override
    public boolean accept(TraccarPositionRequest request) {
        TraccarPositionRequest.Position p = request == null ? null : request.getPosition();
        String badge = request == null || request.getDevice() == null || request.getDevice().getUniqueId() == null
                ? null : request.getDevice().getUniqueId().trim();
        if (p == null || badge == null || badge.isEmpty() || !validCoordinates(p.getLatitude(), p.getLongitude())
                || Boolean.FALSE.equals(p.getValid())) {
            return false;
        }
        List<IncidentDispatch> trips = incidentDispatchRepository.findEnRouteByTrackerBadge(badge);
        if (trips.isEmpty()) {
            return false;
        }
        IncidentDispatch trip = trips.get(0);
        if (trips.size() > 1) {
            log.warn("[TRACCAR] Số hiệu {} đang ở {} lượt điều động, dùng lượt {}", badge, trips.size(), trip.getId());
        }
        OffsetDateTime now = OffsetDateTime.now();
        positions.put(trip.getId(), DispatchPlanResponse.LivePosition.builder()
                .latitude(p.getLatitude())
                .longitude(p.getLongitude())
                .speedKmh(p.getSpeed() == null ? null : Math.round(p.getSpeed() * KNOT_TO_KMH * 10) / 10.0)
                .heading(p.getCourse())
                .accuracyM(p.getAccuracy())
                .fixTime(parseTime(p.getFixTime()))
                .receivedAt(now)
                .source(SOURCE_APP)
                .build());
        positions.values().removeIf(old -> old.getReceivedAt().isBefore(now.minus(KEEP)));
        // lệch tuyến thì tính lại tuyến từ vị trí xe (chạy nền)
        routeRecalcService.onPosition(trip.getId(), p.getLatitude(), p.getLongitude());
        return true;
    }

    @Override
    public boolean acceptBrowser(Long dispatchId, com.sosvietnam.model.payload.request.FieldPositionRequest point) {
        if (dispatchId == null || point == null || !validCoordinates(point.getLatitude(), point.getLongitude())) {
            return false;
        }
        OffsetDateTime now = OffsetDateTime.now();
        DispatchPlanResponse.LivePosition last = positions.get(dispatchId);
        if (last != null && SOURCE_APP.equals(last.getSource()) && last.getReceivedAt().isAfter(now.minus(APP_PRIORITY))) {
            return false; // app đang gửi: giữ điểm của app
        }
        positions.put(dispatchId, DispatchPlanResponse.LivePosition.builder()
                .latitude(point.getLatitude())
                .longitude(point.getLongitude())
                .speedKmh(point.getSpeedKmh() == null ? null : Math.round(point.getSpeedKmh() * 10) / 10.0)
                .heading(point.getHeading())
                .accuracyM(point.getAccuracyM())
                .fixTime(now)
                .receivedAt(now)
                .source(SOURCE_BROWSER)
                .build());
        routeRecalcService.onPosition(dispatchId, point.getLatitude(), point.getLongitude());
        return true;
    }

    @Override
    public DispatchPlanResponse.LivePosition lastPosition(Long dispatchId) {
        return dispatchId == null ? null : positions.get(dispatchId);
    }

    private static boolean validCoordinates(Double lat, Double lng) {
        return lat != null && lng != null && lat >= -90 && lat <= 90 && lng >= -180 && lng <= 180
                && !(lat == 0 && lng == 0);
    }

    private static OffsetDateTime parseTime(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
