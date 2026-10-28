package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentRoute;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentRouteRepository;
import com.sosvietnam.service.DispatchSmsService;
import com.sosvietnam.service.SmsService;
import com.sosvietnam.util.DemoIncidents;
import com.sosvietnam.util.LookupCodes;
import com.sosvietnam.util.RouteSimulation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class DispatchSmsServiceImpl implements DispatchSmsService {

    private final TaskScheduler scheduler;
    private final TransactionTemplate tx;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final IncidentRouteRepository incidentRouteRepository;
    private final SmsService smsService;
    // lượt đang được kiểm / gửi: điểm GPS dồn dập không gửi 2 tin cùng lúc
    private final Set<Long> busy = ConcurrentHashMap.newKeySet();
    // lượt đã báo "khởi hành" (chỉ đổi ACCEPTED -> EN_ROUTE 1 lần; RAM đủ, không cần cột)
    private final Set<Long> departedSent = ConcurrentHashMap.newKeySet();

    @Value("${app.sms.approach-distance-m:50}")
    private double approachDistanceM = 50;

    public DispatchSmsServiceImpl(@Qualifier("incidentTaskScheduler") TaskScheduler scheduler, TransactionTemplate tx,
                                  IncidentDispatchRepository incidentDispatchRepository,
                                  IncidentRouteRepository incidentRouteRepository, SmsService smsService) {
        this.scheduler = scheduler;
        this.tx = tx;
        this.incidentDispatchRepository = incidentDispatchRepository;
        this.incidentRouteRepository = incidentRouteRepository;
        this.smsService = smsService;
    }

    @Override
    public void onDeparted(String incidentId, Long dispatchId) {
        if (dispatchId == null || !smsService.configured()) return;
        scheduler.schedule(() -> {
            try {
                sendDeparted(dispatchId);
            } catch (Exception e) {
                log.warn("📵 [SMS DEPART] {}: {}", incidentId, e.getMessage());
            }
        }, Instant.now());
    }

    /** Đồng bộ (unit test gọi thẳng). @return true khi đã gửi */
    boolean sendDeparted(Long dispatchId) {
        if (departedSent.contains(dispatchId)) return false;
        Boolean sent = tx.execute(s -> {
            IncidentDispatch d = incidentDispatchRepository.findById(dispatchId).orElse(null);
            if (d == null || d.getStatus() != DispatchStatus.EN_ROUTE) return false;
            Incident incident = d.getIncident();
            if (DemoIncidents.isDemo(incident) || incident.getPhone() == null || incident.getPhone().isBlank()) return false;
            IncidentRoute route = incidentRouteRepository
                    .findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(dispatchId).orElse(null);
            Integer minutes = route == null || route.getDurationS() == null ? null
                    : Math.max(1, (int) Math.round(route.getDurationS() / 60.0));
            SmsService.Result result = smsService.send(incident.getPhone(), departedText(incident, d.getFieldMember(), minutes));
            if (!result.sent()) {
                log.warn("📵 [SMS DEPART] {}: {}", incident.getId(), result.error());
                return false;
            }
            log.info("✉️ [SMS DEPART] {} đã báo người báo tin: lực lượng khởi hành", incident.getId());
            return true;
        });
        if (Boolean.TRUE.equals(sent)) departedSent.add(dispatchId);
        return Boolean.TRUE.equals(sent);
    }

    @Override
    public void onPosition(Long dispatchId, double lat, double lng) {
        if (dispatchId == null || !smsService.configured()) return;
        scheduler.schedule(() -> {
            if (!busy.add(dispatchId)) return;
            try {
                checkApproach(dispatchId, new double[]{lat, lng});
            } catch (Exception e) {
                log.warn("📵 [SMS APPROACH] lượt {}: {}", dispatchId, e.getMessage());
            } finally {
                busy.remove(dispatchId);
            }
        }, Instant.now());
    }

    /** Đồng bộ (unit test gọi thẳng). @return true khi đã gửi tin "sắp tới" */
    boolean checkApproach(Long dispatchId, double[] point) {
        // đọc + gửi + ghi mốc trong 1 transaction: lazy incident / route đọc được, mốc ghi cùng lúc
        Boolean sent = tx.execute(s -> {
            IncidentDispatch d = incidentDispatchRepository.findById(dispatchId).orElse(null);
            if (d == null || d.getStatus() != DispatchStatus.EN_ROUTE || d.getApproachSmsAt() != null) return false;
            Incident incident = d.getIncident();
            if (incident.getStatus() != IncidentStatus.RESOLVING || DemoIncidents.isDemo(incident)
                    || incident.getPhone() == null || incident.getPhone().isBlank()) {
                return false;
            }
            IncidentRoute route = incidentRouteRepository
                    .findFirstByDispatch_IdAndSupersededAtIsNullOrderByComputedAtDesc(dispatchId).orElse(null);
            if (route == null || route.getDestLat() == null || route.getDestLng() == null) return false;
            double distance = RouteSimulation.distanceM(point, new double[]{route.getDestLat(), route.getDestLng()});
            if (distance > approachDistanceM) return false;
            SmsService.Result result = smsService.send(incident.getPhone(), approachText(incident));
            if (!result.sent()) {
                log.warn("📵 [SMS APPROACH] {}: {}", incident.getId(), result.error());
                return false; // điểm GPS sau thử lại
            }
            d.setApproachSmsAt(LocalDateTime.now());
            incidentDispatchRepository.save(d);
            log.info("✉️ [SMS APPROACH] {} lực lượng còn ~{} m, đã báo người báo tin", incident.getId(), Math.round(distance));
            return true;
        });
        return Boolean.TRUE.equals(sent);
    }

    @Override
    public void onClosed(String incidentId, Long dispatchId, String resultLabel) {
        if (dispatchId == null || !smsService.configured()) return;
        scheduler.schedule(() -> {
            try {
                sendClosed(dispatchId, resultLabel);
            } catch (Exception e) {
                log.warn("📵 [SMS CLOSED] {}: {}", incidentId, e.getMessage());
            }
        }, Instant.now());
    }

    /** Đồng bộ (unit test gọi thẳng). @return số tin đã gửi */
    int sendClosed(Long dispatchId, String resultLabel) {
        Integer n = tx.execute(s -> {
            IncidentDispatch d = incidentDispatchRepository.findById(dispatchId).orElse(null);
            if (d == null || DemoIncidents.isDemo(d.getIncident())) return 0;
            // người đi hiện trường + cán bộ được uỷ quyền (khác người), mỗi số 1 tin
            Map<String, UnitMember> to = new LinkedHashMap<>();
            for (UnitMember m : new UnitMember[]{d.getFieldMember(), d.getAssignedTo()}) {
                if (m != null && m.getPhone() != null && !m.getPhone().isBlank()) to.putIfAbsent(m.getPhone(), m);
            }
            String text = closedText(d.getIncident(), resultLabel);
            int sent = 0;
            for (String phone : to.keySet()) {
                SmsService.Result result = smsService.send(phone, text);
                if (result.sent()) sent++;
                else log.warn("📵 [SMS CLOSED] {}: {}", d.getIncident().getId(), result.error());
            }
            if (sent > 0) log.info("✉️ [SMS CLOSED] {} đã báo {} người của lực lượng", d.getIncident().getId(), sent);
            return sent;
        });
        return n == null ? 0 : n;
    }

    /** Không dấu, không link, 1 tin (~120 ký tự). */
    static String departedText(Incident incident, UnitMember field, Integer minutes) {
        return "SOS VN: Luc luong chuc nang da khoi hanh toi cho ban"
                + (minutes == null ? "" : ", du kien ~" + minutes + " phut")
                + (field == null ? "" : ". Can bo: " + ascii(field.displayName()))
                + ". Ma ca: " + LookupCodes.format(incident.getLookupCode()) + ".";
    }

    /** Không dấu, không link, 1 tin. */
    static String approachText(Incident incident) {
        return "SOS VN: Luc luong chuc nang sap toi noi (con khoang 50 m). Vui long chu y dien thoai va xac nhan "
                + "tren man hinh theo doi khi luc luong den. Ma ca: " + LookupCodes.format(incident.getLookupCode()) + ".";
    }

    static String closedText(Incident incident, String resultLabel) {
        String result = resultLabel == null ? "" : " (" + ascii(resultLabel) + ")";
        return "SOS VN: Ca " + LookupCodes.format(incident.getLookupCode()) + " da duoc xac nhan ket thuc" + result
                + ". Dong chi ket thuc nhiem vu hien truong. Cam on!";
    }

    /** Bỏ dấu tiếng Việt cho tin SMS (tin có dấu tốn gấp đôi ký tự và dễ bị lọc). */
    static String ascii(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").replace('đ', 'd').replace('Đ', 'D');
    }
}
