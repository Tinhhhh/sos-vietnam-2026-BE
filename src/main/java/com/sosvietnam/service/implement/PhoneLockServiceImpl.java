package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.PhoneLock;
import com.sosvietnam.model.payload.enums.PhoneLockReason;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.PhoneLockResponse;
import com.sosvietnam.repository.PhoneLockRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.service.PhoneLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PhoneLockServiceImpl implements PhoneLockService {

    static final int FALSE_ALARM_LIMIT = 3;
    static final int MISTAKE_LIMIT = 4;
    static final int LOCK_HOURS = 24;
    static final int PERMANENT_AFTER_LOCKS = 3;
    static final int PERMANENT_WINDOW_DAYS = 30;
    private static final int MAX_PAGE_SIZE = 100;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");

    private final PhoneLockRepository phoneLockRepository;
    private final CurrentOfficer currentOfficer;

    @Override
    @Transactional(readOnly = true)
    public void checkCanReport(String phone) {
        if (phone == null || phone.isBlank()) return;
        List<PhoneLock> active = phoneLockRepository.findActiveForPhone(phone, LocalDateTime.now());
        if (active.isEmpty()) return;
        PhoneLock lock = active.stream().filter(PhoneLock::isPermanent).findFirst().orElse(active.get(0));
        String message = lock.isPermanent()
                ? "Số điện thoại này đã bị khoá gửi SOS do báo tin giả nhiều lần. Vui lòng đến cơ quan công an trình bày "
                        + "hoặc liên hệ hệ thống để được gỡ. Khẩn cấp hãy gọi 113 / 114 / 115."
                : "Số điện thoại này tạm bị khoá gửi SOS tới " + TIME.format(lock.getLockedUntil()) + " ("
                        + lock.getReason().getLabel().toLowerCase() + "). Khẩn cấp hãy gọi 113 / 114 / 115.";
        throw new SosException(HttpStatus.FORBIDDEN, message);
    }

    @Override
    @Transactional
    public void onFalseAlarm(String phone) {
        evaluate(phone, PhoneLockReason.FALSE_ALARM);
    }

    @Override
    @Transactional
    public void onMistakeCancel(String phone) {
        evaluate(phone, PhoneLockReason.MISTAKE_CANCEL);
    }

    /**
     * Đếm lỗi trong 24 giờ, nhưng chỉ từ sau lần khoá gần nhất (lỗi đã bị phạt không tính lại khi hết khoá).
     * Đang bị khoá thì thôi (không khoá chồng).
     */
    private void evaluate(String phone, PhoneLockReason reason) {
        if (phone == null || phone.isBlank()) return;
        LocalDateTime now = LocalDateTime.now();
        if (!phoneLockRepository.findActiveForPhone(phone, now).isEmpty()) return;
        LocalDateTime since = now.minusHours(LOCK_HOURS);
        LocalDateTime lastLock = phoneLockRepository.findFirstByPhoneOrderByLockedAtDesc(phone)
                .map(PhoneLock::getLockedAt).orElse(null);
        if (lastLock != null && lastLock.isAfter(since)) since = lastLock;

        boolean reached = reason == PhoneLockReason.FALSE_ALARM
                ? phoneLockRepository.countFalseAlarmsSince(phone, since) >= FALSE_ALARM_LIMIT
                : phoneLockRepository.countMistakeCancelsSince(phone, since) >= MISTAKE_LIMIT;
        if (!reached) return;

        long recentLocks = phoneLockRepository.countByPhoneAndLiftedAtIsNullAndLockedAtGreaterThanEqual(
                phone, now.minusDays(PERMANENT_WINDOW_DAYS));
        boolean permanent = recentLocks + 1 >= PERMANENT_AFTER_LOCKS;
        phoneLockRepository.save(PhoneLock.builder()
                .phone(phone)
                .reason(reason)
                .lockedAt(now)
                .lockedUntil(permanent ? null : now.plusHours(LOCK_HOURS))
                .permanent(permanent)
                .build());
        log.warn("🚫 [PHONE LOCK] {}*** locked {} ({}, lock #{} in {} days)", phone.substring(0, Math.min(4, phone.length())),
                permanent ? "PERMANENTLY" : "for " + LOCK_HOURS + "h", reason, recentLocks + 1, PERMANENT_WINDOW_DAYS);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Long> falseAlarmsByIp(Collection<String> ips) {
        Map<String, Long> out = new HashMap<>();
        if (ips == null || ips.isEmpty()) return out;
        for (Object[] row : phoneLockRepository.countFalseAlarmsByIp(ips, LocalDateTime.now().minusHours(LOCK_HOURS))) {
            out.put((String) row[0], ((Number) row[1]).longValue());
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public ListResponse list(String phone, boolean activeOnly, int pageNo, int pageSize) {
        String p = phone == null || phone.isBlank() ? null : phone.trim();
        PageRequest pageable = PageRequest.of(Math.max(pageNo, 0), Math.min(Math.max(pageSize, 1), MAX_PAGE_SIZE));
        LocalDateTime now = LocalDateTime.now();
        Page<PhoneLock> page = activeOnly
                ? phoneLockRepository.findActive(p, now, pageable)
                : phoneLockRepository.findAllByPhone(p, pageable);
        ListResponse res = new ListResponse();
        res.setContent(page.getContent().stream().map(l -> PhoneLockResponse.from(l, now)).toList());
        res.setPageNo(page.getNumber());
        res.setPageSize(page.getSize());
        res.setTotalElements(page.getTotalElements());
        res.setTotalPages(page.getTotalPages());
        res.setLast(page.isLast());
        return res;
    }

    @Override
    @Transactional
    public PhoneLockResponse lift(Long id, String note) {
        var me = currentOfficer.require();
        if (note == null || note.trim().length() < 5) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Ghi lý do gỡ khoá (ít nhất 5 ký tự)");
        }
        PhoneLock lock = phoneLockRepository.findById(id)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy lần khoá " + id));
        LocalDateTime now = LocalDateTime.now();
        if (!lock.activeAt(now)) throw new SosException(HttpStatus.CONFLICT, "Lần khoá này đã hết hiệu lực");
        lock.setLiftedAt(now);
        lock.setLiftedBy(me.memberId());
        lock.setLiftNote(note.trim());
        PhoneLock saved = phoneLockRepository.save(lock);
        log.info("✅ [PHONE LOCK] lock {} lifted by {}", id, me.email());
        return PhoneLockResponse.from(saved, now);
    }
}
