package com.sosvietnam;

import com.sosvietnam.controller.TraccarController;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.TraccarPositionRequest;
import com.sosvietnam.model.payload.response.DispatchPlanResponse;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.service.DispatchTrackingService;
import com.sosvietnam.service.implement.DispatchTrackingServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** Traccar -> /internal/traccar/positions: secret, matching the EN_ROUTE trip by badge, last point in RAM. */
class DispatchTrackingTest {

    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final com.sosvietnam.service.RouteRecalcService recalc = mock(com.sosvietnam.service.RouteRecalcService.class);
    final DispatchTrackingService tracking = new DispatchTrackingServiceImpl(dispatches, recalc);

    static TraccarPositionRequest point(String badge, Double lat, Double lng) {
        return TraccarPositionRequest.builder()
                .device(TraccarPositionRequest.Device.builder().uniqueId(badge).build())
                .position(TraccarPositionRequest.Position.builder().latitude(lat).longitude(lng)
                        .speed(10.0).course(90.0).accuracy(8.0).fixTime("2026-10-25T08:00:05.000+07:00").build())
                .build();
    }

    static IncidentDispatch trip(long id) {
        return IncidentDispatch.builder().id(id).build();
    }

    @Test
    void enRouteTripOfTheBadge_keepsLatestPoint_inKmh() {
        when(dispatches.findEnRouteByTrackerBadge("594288")).thenReturn(List.of(trip(7L), trip(3L)));

        assertThat(tracking.accept(point(" 594288 ", 10.0455, 105.7865))).isTrue();
        assertThat(tracking.accept(point("594288", 10.0460, 105.7870))).isTrue();

        DispatchPlanResponse.LivePosition p = tracking.lastPosition(7L);
        assertThat(p.getLatitude()).isEqualTo(10.0460);
        assertThat(p.getLongitude()).isEqualTo(105.7870);
        assertThat(p.getSpeedKmh()).isEqualTo(18.5); // 10 knot
        assertThat(p.getHeading()).isEqualTo(90.0);
        assertThat(p.getFixTime()).isNotNull();
        assertThat(p.getReceivedAt()).isNotNull();
        assertThat(tracking.lastPosition(3L)).isNull(); // only the newest trip of a reused badge
    }

    @Test
    void ignoresUnknownDevices_badCoordinates_invalidFix() {
        when(dispatches.findEnRouteByTrackerBadge(anyString())).thenReturn(List.of());
        assertThat(tracking.accept(point("000000", 10.0, 105.0))).isFalse();

        when(dispatches.findEnRouteByTrackerBadge("1")).thenReturn(List.of(trip(1L)));
        assertThat(tracking.accept(point("1", 95.0, 105.0))).isFalse();
        assertThat(tracking.accept(point("1", 0.0, 0.0))).isFalse();
        assertThat(tracking.accept(point("1", null, 105.0))).isFalse();
        assertThat(tracking.accept(point("", 10.0, 105.0))).isFalse();
        TraccarPositionRequest invalid = point("1", 10.0, 105.0);
        invalid.getPosition().setValid(false);
        assertThat(tracking.accept(invalid)).isFalse();
        assertThat(tracking.accept(null)).isFalse();
        assertThat(tracking.lastPosition(1L)).isNull();
    }

    @Test
    void controller_rejectsMissingOrWrongSecret_evenWhenNotConfigured() {
        DispatchTrackingService service = mock(DispatchTrackingService.class);
        TraccarController controller = new TraccarController(service);
        TraccarPositionRequest body = point("1", 10.0, 105.0);

        // secret not configured: everything is refused
        ReflectionTestUtils.setField(controller, "forwardSecret", "");
        assertThatThrownBy(() -> controller.receivePosition("", body))
                .isInstanceOf(SosException.class)
                .satisfies(e -> assertThat(((SosException) e).getHttpStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));

        ReflectionTestUtils.setField(controller, "forwardSecret", "s3cret-test");
        assertThatThrownBy(() -> controller.receivePosition(null, body)).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> controller.receivePosition("wrong", body)).isInstanceOf(SosException.class);
        verifyNoInteractions(service);

        when(service.accept(body)).thenReturn(true);
        assertThat(controller.receivePosition("s3cret-test", body).getStatusCode().value()).isEqualTo(200);
        verify(service).accept(body);
    }
}
