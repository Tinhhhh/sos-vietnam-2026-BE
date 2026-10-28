package com.sosvietnam;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.SmsService;
import com.sosvietnam.service.implement.CitizenSmsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** SMS "đã tiếp nhận" + mã ca cho người báo tin: 1 lần / ca, sau ~10 giây, 20 tin / số / 24 giờ, bỏ ca demo / ca đã đóng. */
class CitizenSmsTest {

    final TaskScheduler scheduler = mock(TaskScheduler.class);
    final IncidentRepository incidents = mock(IncidentRepository.class);
    final SmsService sms = mock(SmsService.class);
    final CitizenSmsServiceImpl service = new CitizenSmsServiceImpl(scheduler, incidents, sms);
    Incident incident;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "publicUrl", "https://sosvie.site/");
        incident = Incident.builder().id("SOS-1").citizenName("Võ Văn Tịnh").phone("0912345678").lookupCode("K7M2QX9P")
                .status(IncidentStatus.VERIFYING).createdAt(LocalDateTime.now()).build();
        when(incidents.findById("SOS-1")).thenReturn(Optional.of(incident));
        when(incidents.save(any())).thenAnswer(i -> i.getArgument(0));
        when(sms.configured()).thenReturn(true);
        when(sms.send(anyString(), anyString())).thenReturn(new SmsService.Result(true, null));
    }

    /** Chạy ngay việc đã hẹn (thay cho chờ 10 giây). */
    void runScheduled() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler, atLeastOnce()).schedule(task.capture(), any(Instant.class));
        task.getValue().run();
    }

    @Test
    void claimed_sendsOnce_withTheCaseCode_noLink_oneSms() {
        service.scheduleReceived("SOS-1");
        runScheduled();
        ArgumentCaptor<String> text = ArgumentCaptor.forClass(String.class);
        verify(sms).send(eq("0912345678"), text.capture());
        assertThat(text.getValue()).contains("Ma ca: K7M2-QX9P").contains("sosvie.site > Tra cuu")
                .doesNotContain("http").matches("\\p{ASCII}+").hasSizeLessThanOrEqualTo(160);
        assertThat(incident.getReceivedSmsAt()).isNotNull();

        // nhận lại ca (nhả / giao / lên tỉnh): không gửi thêm
        reset(scheduler);
        service.scheduleReceived("SOS-1");
        runScheduled();
        verify(sms, times(1)).send(anyString(), anyString());
    }

    @Test
    void cancelledOrFalseAlarmBeforeSending_demo_noPhone_notSent() {
        service.scheduleReceived("SOS-1");
        incident.setStatus(IncidentStatus.CANCELLED); // huỷ / báo khống trong 10 giây chờ
        runScheduled();

        incident.setStatus(IncidentStatus.VERIFYING);
        incident.setCitizenName("[DEMO] Nguyễn Văn Test");
        reset(scheduler);
        service.scheduleReceived("SOS-1");
        runScheduled();

        incident.setCitizenName("Võ Văn Tịnh");
        incident.setPhone(null);
        reset(scheduler);
        service.scheduleReceived("SOS-1");
        runScheduled();

        verify(sms, never()).send(anyString(), anyString());
        assertThat(incident.getReceivedSmsAt()).isNull();
    }

    @Test
    void twentySmsToThePhoneIn24h_stops() {
        when(incidents.countByPhoneAndReceivedSmsAtAfter(eq("0912345678"), any())).thenReturn(20L);
        service.scheduleReceived("SOS-1");
        runScheduled();
        verify(sms, never()).send(anyString(), anyString());
    }

    @Test
    void gatewayNotConfigured_nothingScheduled_failedSend_notMarked() {
        when(sms.configured()).thenReturn(false);
        service.scheduleReceived("SOS-1");
        verifyNoInteractions(scheduler);

        when(sms.configured()).thenReturn(true);
        when(sms.send(anyString(), anyString())).thenReturn(new SmsService.Result(false, "Cổng SMS lỗi (HTTP 500)"));
        service.scheduleReceived("SOS-1");
        runScheduled();
        assertThat(incident.getReceivedSmsAt()).isNull(); // lần nhận ca sau thử lại
    }
}
