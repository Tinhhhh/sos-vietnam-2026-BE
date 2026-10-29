package com.sosvietnam;

import com.sosvietnam.model.entity.PhoneLock;
import com.sosvietnam.model.payload.enums.PhoneLockReason;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.repository.AppSettingRepository;
import com.sosvietnam.repository.PhoneLockRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.service.implement.PhoneLockServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Khoá SĐT báo tin giả: 3 báo khống / 4 bấm nhầm trong 24 giờ -> 24 giờ; lần khoá thứ 3 trong 30 ngày -> vĩnh viễn. */
class PhoneLockServiceTest {

    static final String PHONE = "0912345678";
    final PhoneLockRepository repo = mock(PhoneLockRepository.class);
    final PhoneLockServiceImpl service = new PhoneLockServiceImpl(repo, mock(AppSettingRepository.class), new CurrentOfficer());

    PhoneLock saved() {
        ArgumentCaptor<PhoneLock> c = ArgumentCaptor.forClass(PhoneLock.class);
        verify(repo).save(c.capture());
        return c.getValue();
    }

    @Test
    void twoFalseAlarms_noLock_third_locks24h() {
        when(repo.countFalseAlarmsSince(eq(PHONE), any())).thenReturn(2L);
        service.onFalseAlarm(PHONE);
        verify(repo, never()).save(any());

        when(repo.countFalseAlarmsSince(eq(PHONE), any())).thenReturn(3L);
        service.onFalseAlarm(PHONE);
        PhoneLock lock = saved();
        assertThat(lock.getReason()).isEqualTo(PhoneLockReason.FALSE_ALARM);
        assertThat(lock.isPermanent()).isFalse();
        assertThat(lock.getLockedUntil()).isAfter(LocalDateTime.now().plusHours(23));
    }

    @Test
    void fourthMistakeCancel_locks() {
        when(repo.countMistakeCancelsSince(eq(PHONE), any())).thenReturn(3L);
        service.onMistakeCancel(PHONE);
        verify(repo, never()).save(any());
        when(repo.countMistakeCancelsSince(eq(PHONE), any())).thenReturn(4L);
        service.onMistakeCancel(PHONE);
        assertThat(saved().getReason()).isEqualTo(PhoneLockReason.MISTAKE_CANCEL);
    }

    @Test
    void thirdLockIn30Days_isPermanent() {
        when(repo.countFalseAlarmsSince(eq(PHONE), any())).thenReturn(3L);
        when(repo.countByPhoneAndLiftedAtIsNullAndLockedAtGreaterThanEqual(eq(PHONE), any())).thenReturn(2L);
        service.onFalseAlarm(PHONE);
        PhoneLock lock = saved();
        assertThat(lock.isPermanent()).isTrue();
        assertThat(lock.getLockedUntil()).isNull();
    }

    @Test
    void strikesAreCountedOnlyAfterTheLastLock() {
        LocalDateTime last = LocalDateTime.now().minusHours(2);
        when(repo.findFirstByPhoneOrderByLockedAtDesc(PHONE)).thenReturn(Optional.of(PhoneLock.builder().lockedAt(last).build()));
        service.onFalseAlarm(PHONE);
        verify(repo).countFalseAlarmsSince(PHONE, last);
    }

    @Test
    void lockedPhone_cannotReport_403_unlocked_can() {
        service.checkCanReport(PHONE); // no lock
        PhoneLock lock = PhoneLock.builder().phone(PHONE).reason(PhoneLockReason.FALSE_ALARM)
                .lockedAt(LocalDateTime.now()).lockedUntil(LocalDateTime.now().plusHours(24)).build();
        when(repo.findActiveForPhone(eq(PHONE), any())).thenReturn(List.of(lock));
        assertThatThrownBy(() -> service.checkCanReport(PHONE)).isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
        // đang bị khoá thì không khoá chồng
        when(repo.countFalseAlarmsSince(eq(PHONE), any())).thenReturn(9L);
        service.onFalseAlarm(PHONE);
        verify(repo, never()).save(any());
    }
}
