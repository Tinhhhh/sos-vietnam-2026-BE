package com.sosvietnam.config;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.IncidentService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ca VERIFYING chỉ tự trả về hàng đợi khi người giữ mất kết nối liên tục quá thời hạn. */
class HeldIncidentWatchdogTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final AccountRepository accountRepo = mock(AccountRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final IncidentService service = mock(IncidentService.class);
    final HeldIncidentWatchdog watchdog = new HeldIncidentWatchdog(incidentRepo, accountRepo, notifier, service);

    final UUID holder = UUID.randomUUID();

    HeldIncidentWatchdogTest() {
        ReflectionTestUtils.setField(watchdog, "offlineReleaseMinutes", 3L);
        when(incidentRepo.findByStatusAndHandlerIdIsNotNull(IncidentStatus.VERIFYING))
                .thenReturn(List.of(Incident.builder().id("SOS-1").handlerId(holder).build()));
        when(accountRepo.findAllById(any())).thenReturn(List.of(Account.builder().id(holder).email("a@sos.vn").build()));
        when(service.autoRelease(any(), any(), any())).thenReturn(true);
    }

    @Test
    void onlineHolder_keepsTheCase() {
        when(notifier.onlineEmails()).thenReturn(Set.of("a@sos.vn"));
        LocalDateTime t = LocalDateTime.now();
        watchdog.check(t);
        watchdog.check(t.plusMinutes(10));
        verify(service, never()).autoRelease(any(), any(), any());
    }

    @Test
    void offlineHolder_releasedOnlyAfterTheGracePeriod() {
        when(notifier.onlineEmails()).thenReturn(Set.of());
        LocalDateTime t = LocalDateTime.now();
        watchdog.check(t);
        watchdog.check(t.plusMinutes(2));
        verify(service, never()).autoRelease(any(), any(), any());
        watchdog.check(t.plusMinutes(3).plusSeconds(1));
        verify(service).autoRelease(eq("SOS-1"), eq(holder), contains("mất kết nối"));
    }

    @Test
    void reconnectingResetsTheClock() {
        LocalDateTime t = LocalDateTime.now();
        when(notifier.onlineEmails()).thenReturn(Set.of());
        watchdog.check(t);
        when(notifier.onlineEmails()).thenReturn(Set.of("a@sos.vn"));
        watchdog.check(t.plusMinutes(2));
        when(notifier.onlineEmails()).thenReturn(Set.of());
        watchdog.check(t.plusMinutes(4));
        verify(service, never()).autoRelease(any(), any(), any());
    }
}
