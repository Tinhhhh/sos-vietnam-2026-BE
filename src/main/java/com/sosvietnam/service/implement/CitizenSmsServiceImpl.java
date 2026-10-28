package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.CitizenSmsService;
import com.sosvietnam.service.SmsService;
import com.sosvietnam.util.DemoIncidents;
import com.sosvietnam.util.LookupCodes;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tin "đã tiếp nhận" cho người báo tin (docs/spec-multi-case.md). Nhận ca -> hẹn gửi sau
 * {@code app.sms.received-delay-seconds}; tới giờ đọc lại ca: còn mở, chưa gửi, không phải ca demo, có SĐT, số đó chưa
 * nhận đủ {@code app.sms.max-per-phone-per-day} tin trong 24 giờ thì gửi rồi ghi {@code incidents.received_sms_at}.
 * Gửi hỏng (cổng lỗi) thì không ghi: lần nhận ca sau thử lại.
 */
@Service
@Slf4j
public class CitizenSmsServiceImpl implements CitizenSmsService {

    private final TaskScheduler scheduler;
    private final IncidentRepository incidentRepository;
    private final SmsService smsService;
    // ca đang chờ tới giờ gửi: nhả rồi nhận lại trong vài giây không hẹn thêm lần nữa
    private final Set<String> pending = ConcurrentHashMap.newKeySet();

    @Value("${app.sms.received-delay-seconds:10}")
    private long delaySeconds = 10;
    @Value("${app.sms.max-per-phone-per-day:20}")
    private long maxPerPhonePerDay = 20;
    @Value("${app.public-url:http://localhost:5173}")
    private String publicUrl = "http://localhost:5173";

    public CitizenSmsServiceImpl(@Qualifier("incidentTaskScheduler") TaskScheduler scheduler,
                                 IncidentRepository incidentRepository, SmsService smsService) {
        this.scheduler = scheduler;
        this.incidentRepository = incidentRepository;
        this.smsService = smsService;
    }

    @Override
    public void scheduleReceived(String incidentId) {
        if (!smsService.configured() || !pending.add(incidentId)) return;
        scheduler.schedule(() -> {
            try {
                sendReceived(incidentId);
            } catch (Exception e) {
                log.warn("📵 [SMS RECEIVED] {}: {}", incidentId, e.getMessage());
            } finally {
                pending.remove(incidentId);
            }
        }, Instant.now().plus(Duration.ofSeconds(delaySeconds)));
    }

    /** Tới giờ gửi: kiểm lại mọi điều kiện trên bản ca mới nhất. @return true khi đã gửi */
    boolean sendReceived(String incidentId) {
        Incident incident = incidentRepository.findById(incidentId).orElse(null);
        if (incident == null || !IncidentStatus.ACTIVE.contains(incident.getStatus())   // huỷ / báo khống trong lúc chờ
                || incident.getReceivedSmsAt() != null || DemoIncidents.isDemo(incident)
                || incident.getPhone() == null || incident.getPhone().isBlank()) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (incidentRepository.countByPhoneAndReceivedSmsAtAfter(incident.getPhone(), now.minusHours(24)) >= maxPerPhonePerDay) {
            log.info("📵 [SMS RECEIVED] {}: số đã nhận đủ {} tin trong 24 giờ, bỏ qua", incidentId, maxPerPhonePerDay);
            return false;
        }
        SmsService.Result result = smsService.send(incident.getPhone(), text(incident));
        if (!result.sent()) {
            log.warn("📵 [SMS RECEIVED] {}: {}", incidentId, result.error());
            return false;
        }
        incident.setReceivedSmsAt(now);
        incidentRepository.save(incident);
        log.info("✉️ [SMS RECEIVED] {} đã gửi tin tiếp nhận", incidentId);
        return true;
    }

    /** Không dấu, không link (~140 ký tự, 1 tin): mã ca + cách Tra cứu. */
    String text(Incident incident) {
        String site = publicUrl.replaceFirst("^https?://", "").replaceAll("/+$", "");
        return "SOS VN: Tin bao cua ban da duoc tiep nhan, can bo dang lien he. Ma ca: "
                + LookupCodes.format(incident.getLookupCode())
                + ". Mat ket noi: vao " + site + " > Tra cuu, nhap ma + 4 so cuoi SDT.";
    }
}
