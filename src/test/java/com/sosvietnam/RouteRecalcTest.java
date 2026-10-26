package com.sosvietnam;

import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.RouteOriginSource;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentRouteRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.RoutingService;
import com.sosvietnam.service.implement.RouteRecalcServiceImpl;
import com.sosvietnam.util.RouteSimulation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tự tính lại tuyến khi xe lệch đường (25/10/2026): 2 điểm lệch liên tiếp > 50 m, tuyến mới từ vị trí xe. */
class RouteRecalcTest {

    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final IncidentRouteRepository routes = mock(IncidentRouteRepository.class);
    final RoutingService routing = mock(RoutingService.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final RouteRecalcServiceImpl recalc = new RouteRecalcServiceImpl(mock(TaskScheduler.class), tx, dispatches, routes,
            routing, notifier);

    IncidentDispatch trip;
    IncidentRoute route;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(tx.execute(any())).thenAnswer(i -> ((TransactionCallback<Object>) i.getArgument(0)).doInTransaction(null));
        trip = IncidentDispatch.builder().id(11L).status(DispatchStatus.EN_ROUTE).build();
        // tuyến thẳng hướng bắc ~1,1 km
        route = IncidentRoute.builder().id(5L).dispatch(trip).incidentId("SOS-1").stationId(7L)
                .originLat(10.0).originLng(105.0).originSource(RouteOriginSource.STATION)
                .destLat(10.01).destLng(105.0).polyline(encodeNorth()).build();
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(11L)).thenReturn(Optional.of(route));
        when(routes.findById(5L)).thenReturn(Optional.of(route));
        when(dispatches.findById(11L)).thenReturn(Optional.of(trip));
        when(routing.route(any())).thenReturn(new RoutingService.RoadRoute("abc",
                List.of(new double[]{10.005, 105.003}, new double[]{10.01, 105.0}), 800, 90));
    }

    /** (10.0, 105.0) -> (10.01, 105.0) theo định dạng Google 1e-5. */
    static String encodeNorth() {
        return com.sosvietnam.util.Polylines.encode(List.of(new double[]{10.0, 105.0}, new double[]{10.01, 105.0}));
    }

    @Test
    void onTheRoute_nothingHappens() {
        recalc.check(11L, new double[]{10.005, 105.0001}); // ~11 m
        recalc.check(11L, new double[]{10.006, 105.0001});
        verifyNoInteractions(routing);
    }

    @Test
    void twoPointsOffTheRoute_newRouteFromTheVehicle() {
        double[] off = RouteSimulation.offset(new double[]{10.005, 105.0}, 90, 300); // 300 m về phía đông
        recalc.check(11L, off);
        verifyNoInteractions(routing); // 1 điểm lệch có thể là GPS nhiễu
        recalc.check(11L, off);

        ArgumentCaptor<IncidentRoute> saved = ArgumentCaptor.forClass(IncidentRoute.class);
        verify(routes, times(2)).save(saved.capture());
        assertThat(saved.getAllValues().get(0).getSupersededAt()).isNotNull(); // tuyến cũ giữ lại
        IncidentRoute fresh = saved.getAllValues().get(1);
        assertThat(fresh.getOriginSource()).isEqualTo(RouteOriginSource.VEHICLE);
        assertThat(fresh.getOriginLat()).isEqualTo(off[0]);
        assertThat(fresh.getDestLat()).isEqualTo(10.01);
        assertThat(fresh.getDistanceM()).isEqualTo(800);
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);

        // ngay sau đó vẫn lệch: chờ hết thời gian nghỉ, không gọi máy chủ chỉ đường dồn dập
        recalc.check(11L, off);
        recalc.check(11L, off);
        verify(routing, times(1)).route(any());
    }

    @Test
    void estimatedRouteWithoutPolyline_usesTheStraightLine() {
        route.setPolyline(null); // tuyến ước lượng: đường thẳng (10.0,105.0) -> (10.01,105.0)
        double[] near = RouteSimulation.offset(new double[]{10.005, 105.0}, 90, 20);
        recalc.check(11L, near);
        recalc.check(11L, near);
        verifyNoInteractions(routing);
        double[] off = RouteSimulation.offset(new double[]{10.005, 105.0}, 90, 300);
        recalc.check(11L, off);
        recalc.check(11L, off);
        verify(routing).route(any());
    }

    @Test
    void tripNoLongerOnTheWay_keepsTheRoute() {
        trip.setStatus(DispatchStatus.ARRIVED);
        double[] off = RouteSimulation.offset(new double[]{10.005, 105.0}, 90, 300);
        recalc.check(11L, off);
        recalc.check(11L, off);
        verify(routes, never()).save(any());
        verifyNoInteractions(notifier);
    }

    @Test
    void distanceToPath() {
        List<double[]> line = List.of(new double[]{10.0, 105.0}, new double[]{10.01, 105.0});
        double[] p = RouteSimulation.offset(new double[]{10.005, 105.0}, 90, 120);
        assertThat(RouteSimulation.distanceToPathM(line, p)).isBetween(118.0, 122.0);
    }
}
