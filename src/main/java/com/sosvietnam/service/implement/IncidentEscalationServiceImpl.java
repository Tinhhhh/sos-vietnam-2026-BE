package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

@Service
@Slf4j
public class IncidentEscalationServiceImpl implements IncidentEscalationService {

    private final IncidentRepository incidentRepository;
    private final IncidentNotifier notifier;
    private final TaskScheduler scheduler;
    private final TransactionTemplate transactionTemplate;

    public IncidentEscalationServiceImpl(IncidentRepository incidentRepository, IncidentNotifier notifier,
                                         @Qualifier("incidentTaskScheduler") TaskScheduler scheduler,
                                         TransactionTemplate transactionTemplate) {
        this.incidentRepository = incidentRepository;
        this.notifier = notifier;
        this.scheduler = scheduler;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void schedule(Incident incident) {
        if (incident.getEscalateAt() == null || incident.getEscalatedAt() != null
                || incident.getRoutedLevel() != UnitLevel.WARD || incident.getStatus() != IncidentStatus.PENDING) {
            return;
        }
        Instant at = incident.getEscalateAt().atZone(ZoneId.systemDefault()).toInstant();
        Instant now = Instant.now();
        String id = incident.getId();
        // Quá hạn rồi (vd ca bị nhả sau mốc 15 phút) thì leo thang ngay
        scheduler.schedule(() -> escalateIfDue(id), at.isBefore(now) ? now : at);
    }

    @Override
    public boolean escalateIfDue(String incidentId) {
        Integer rows = transactionTemplate.execute(status -> incidentRepository.escalate(incidentId, LocalDateTime.now()));
        boolean escalated = rows != null && rows > 0;
        if (escalated) {
            log.info("⚡ [ESCALATION] Incident {} not accepted in time, moved to province level", incidentId);
            notifier.notify(incidentId, IncidentEvent.INCIDENT_ESCALATED);
        }
        return escalated;
    }

    /** Hẹn lại các ca đang chờ khi BE vừa khởi động. */
    @EventListener(ApplicationReadyEvent.class)
    public void rescheduleOnStartup() {
        try {
            List<Incident> waiting = incidentRepository.findAwaitingEscalation();
            waiting.forEach(this::schedule);
            if (!waiting.isEmpty()) {
                log.info("⏱ [ESCALATION] Rescheduled {} waiting ward incident(s)", waiting.size());
            }
        } catch (Exception e) {
            log.error("❌ [ESCALATION] Could not reschedule waiting incidents: {}", e.getMessage(), e);
        }
    }
}
