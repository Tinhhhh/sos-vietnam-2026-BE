package com.sosvietnam;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentRouteRepository;
import com.sosvietnam.service.SmsService;
import com.sosvietnam.service.implement.DispatchSmsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** SMS "sắp tới" (<= 50 m, 1 lần / lượt) cho người báo tin, "ca đã kết thúc" cho lực lượng đi. */
class DispatchSmsTest {

    final IncidentDispatchRepository dispatches = mock(IncidentDispatchRepository.class);
    final IncidentRouteRepository routes = mock(IncidentRouteRepository.class);
    final SmsService sms = mock(SmsService.class);
    final TransactionTemplate tx = mock(TransactionTemplate.class);
    final DispatchSmsServiceImpl service = new DispatchSmsServiceImpl(mock(TaskScheduler.class), tx, dispatches, routes, sms);

    Incident incident;
    IncidentDispatch d;
    static final double DEST_LAT = 10.0455, DEST_LNG = 105.7865;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        when(tx.execute(any())).thenAnswer(inv -> ((TransactionCallback<Object>) inv.getArgument(0)).doInTransaction(null));
        when(sms.configured()).thenReturn(true);
        when(sms.send(anyString(), anyString())).thenReturn(new SmsService.Result(true, null));
        incident = Incident.builder().id("SOS-1").status(IncidentStatus.RESOLVING).phone("0912345678")
                .citizenName("Nguyễn Văn Dân").lookupCode("K7M2QX9P").build();
        d = IncidentDispatch.builder().id(7L).incident(incident).status(DispatchStatus.EN_ROUTE).build();
        when(dispatches.findById(7L)).thenReturn(Optional.of(d));
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(7L))
                .thenReturn(Optional.of(IncidentRoute.builder().destLat(DEST_LAT).destLng(DEST_LNG).build()));
    }

    boolean approach(double lat, double lng) throws Exception {
        Method m = DispatchSmsServiceImpl.class.getDeclaredMethod("checkApproach", Long.class, double[].class);
        m.setAccessible(true);
        return (boolean) m.invoke(service, 7L, new double[]{lat, lng});
    }

    int closed(String label) throws Exception {
        Method m = DispatchSmsServiceImpl.class.getDeclaredMethod("sendClosed", Long.class, String.class);
        m.setAccessible(true);
        return (int) m.invoke(service, 7L, label);
    }

    @Test
    void farAway_noSms_within50m_onceOnly() throws Exception {
        assertThat(approach(DEST_LAT + 0.002, DEST_LNG)).isFalse(); // ~220 m
        verify(sms, never()).send(anyString(), anyString());

        assertThat(approach(DEST_LAT + 0.0003, DEST_LNG)).isTrue(); // ~33 m
        verify(sms).send(eq("0912345678"), contains("sap toi"));
        assertThat(d.getApproachSmsAt()).isNotNull();

        assertThat(approach(DEST_LAT, DEST_LNG)).isFalse(); // đã gửi rồi
        verify(sms, times(1)).send(anyString(), anyString());
    }

    @Test
    void approach_notSent_whenNotOnTheWay_orDemo_orNoPhone_orGatewayFails() throws Exception {
        d.setStatus(DispatchStatus.ARRIVED);
        assertThat(approach(DEST_LAT, DEST_LNG)).isFalse();
        d.setStatus(DispatchStatus.EN_ROUTE);
        incident.setCitizenName("[DEMO] Nguyễn Văn A");
        assertThat(approach(DEST_LAT, DEST_LNG)).isFalse();
        incident.setCitizenName("Nguyễn Văn Dân");
        incident.setPhone(null);
        assertThat(approach(DEST_LAT, DEST_LNG)).isFalse();
        incident.setPhone("0912345678");
        when(sms.send(anyString(), anyString())).thenReturn(new SmsService.Result(false, "gateway down"));
        assertThat(approach(DEST_LAT, DEST_LNG)).isFalse();
        assertThat(d.getApproachSmsAt()).isNull(); // điểm sau thử lại
    }

    @Test
    void departed_smsToReporter_withEtaAndOfficer_once() throws Exception {
        d.setFieldMember(UnitMember.builder().id(UUID.randomUUID()).fullName("Đỗ Đức Khang").phone("0900000001").build());
        when(routes.findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(7L))
                .thenReturn(Optional.of(IncidentRoute.builder().destLat(DEST_LAT).destLng(DEST_LNG).durationS(480).build()));
        Method m = DispatchSmsServiceImpl.class.getDeclaredMethod("sendDeparted", Long.class);
        m.setAccessible(true);
        assertThat((boolean) m.invoke(service, 7L)).isTrue();
        verify(sms).send(eq("0912345678"), argThat(t -> t.contains("da khoi hanh") && t.contains("~8 phut")
                && t.contains("Do Duc Khang") && t.contains("Ma ca: K7M2-QX9P")));
        assertThat((boolean) m.invoke(service, 7L)).isFalse(); // không gửi lần 2
        verify(sms, times(1)).send(anyString(), anyString());
    }

    @Test
    void closed_smsToFieldOfficerAndDelegate_once_perPhone() throws Exception {
        UnitMember field = UnitMember.builder().id(UUID.randomUUID()).fullName("A").phone("0900000001").build();
        UnitMember delegate = UnitMember.builder().id(UUID.randomUUID()).fullName("B").phone("0900000002").build();
        d.setFieldMember(field);
        d.setAssignedTo(delegate);
        assertThat(closed("Đã xử lý xong")).isEqualTo(2);
        verify(sms).send(eq("0900000001"), contains("da duoc xac nhan ket thuc (Da xu ly xong)"));
        verify(sms).send(eq("0900000002"), anyString());

        reset(sms);
        when(sms.send(anyString(), anyString())).thenReturn(new SmsService.Result(true, null));
        d.setAssignedTo(field); // cùng người: 1 tin
        assertThat(closed("Báo khống")).isEqualTo(1);
    }
}
