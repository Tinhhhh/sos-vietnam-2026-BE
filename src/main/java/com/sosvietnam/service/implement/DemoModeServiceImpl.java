package com.sosvietnam.service.implement;

import com.sosvietnam.model.payload.enums.DemoPurgeScope;
import com.sosvietnam.model.payload.response.DemoPurgeResponse;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.service.DispatchSimulationService;
import com.sosvietnam.model.entity.AppSetting;
import com.sosvietnam.model.payload.enums.AppSettingKey;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.DemoSettingsRequest;
import com.sosvietnam.model.payload.response.DemoSettingsResponse;
import com.sosvietnam.repository.AppSettingRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.UnitMemberRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.DemoModeService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.util.DemoIncidents;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Công tắc ca demo, dùng chung cho mọi tài khoản. Lưu ở app_settings (đổi có hiệu lực ngay, restart vẫn giữ);
 * chưa có dòng thì: tự sinh = DEMO_INCIDENTS của .env, ẩn = tắt. Đọc nhiều (mỗi lần gọi hàng đợi, mỗi tin STOMP)
 * nên giữ bản sao trong RAM, chỉ đúng khi 1 instance BE.
 */
@Slf4j
@Service
public class DemoModeServiceImpl implements DemoModeService {

    private final AppSettingRepository appSettingRepository;
    private final IncidentRepository incidentRepository;
    private final UnitMemberRepository unitMemberRepository;
    private final CurrentOfficer currentOfficer;
    private final IncidentNotifier notifier;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final DispatchSimulationService dispatchSimulationService;
    private final boolean generateDefault;
    private final double speedDefault;
    static final double MIN_SPEED_KMH = 5;
    static final double MAX_SPEED_KMH = 300;

    private volatile Map<AppSettingKey, AppSetting> cache;

    // @Lazy: IncidentNotifierImpl cũng hỏi service này (ẩn ca demo khỏi tin STOMP), mô phỏng hỏi tốc độ
    public DemoModeServiceImpl(AppSettingRepository appSettingRepository, IncidentRepository incidentRepository,
                               UnitMemberRepository unitMemberRepository, CurrentOfficer currentOfficer,
                               @Lazy IncidentNotifier notifier,
                               IncidentDispatchRepository incidentDispatchRepository,
                               @Lazy DispatchSimulationService dispatchSimulationService,
                               @Value("${app.demo.incidents.enabled:false}") boolean generateDefault,
                               @Value("${app.traccar.simulation.speed-kmh:50}") double speedDefault) {
        this.appSettingRepository = appSettingRepository;
        this.incidentRepository = incidentRepository;
        this.unitMemberRepository = unitMemberRepository;
        this.currentOfficer = currentOfficer;
        this.notifier = notifier;
        this.incidentDispatchRepository = incidentDispatchRepository;
        this.dispatchSimulationService = dispatchSimulationService;
        this.generateDefault = generateDefault;
        this.speedDefault = speedDefault;
    }

    @Override
    public boolean generateEnabled() {
        return flag(AppSettingKey.DEMO_GENERATE, generateDefault);
    }

    @Override
    public boolean hidden() {
        return flag(AppSettingKey.DEMO_HIDDEN, false);
    }

    @Override
    public double simulationSpeedKmh() {
        AppSetting row = settings().get(AppSettingKey.SIMULATION_SPEED_KMH);
        if (row == null) return speedDefault;
        try {
            return Double.parseDouble(row.getValue());
        } catch (NumberFormatException e) {
            return speedDefault;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public DemoSettingsResponse get() {
        AppSetting last = settings().values().stream()
                .max(Comparator.comparing(AppSetting::getUpdatedAt)).orElse(null);
        String by = last == null || last.getUpdatedBy() == null ? null
                : unitMemberRepository.findById(last.getUpdatedBy()).map(m -> m.displayName()).orElse(null);
        return DemoSettingsResponse.builder()
                .generate(generateEnabled())
                .hidden(hidden())
                .simulationSpeedKmh(simulationSpeedKmh())
                .openCount(incidentRepository.findDemoIds(IncidentStatus.ACTIVE, DemoIncidents.LEGACY_NAMES).size())
                .totalCount(incidentRepository.countDemo(DemoIncidents.LEGACY_NAMES))
                .updatedAt(last == null ? null : last.getUpdatedAt())
                .updatedByName(by)
                .build();
    }

    @Override
    @Transactional
    public DemoSettingsResponse update(DemoSettingsRequest request) {
        OfficerPrincipal me = currentOfficer.require();
        boolean wasHidden = hidden();
        if (request.getGenerate() != null) save(AppSettingKey.DEMO_GENERATE, request.getGenerate(), me);
        if (request.getHidden() != null) save(AppSettingKey.DEMO_HIDDEN, request.getHidden(), me);
        if (request.getSimulationSpeedKmh() != null) {
            double v = request.getSimulationSpeedKmh();
            if (Double.isNaN(v) || v < MIN_SPEED_KMH || v > MAX_SPEED_KMH) {
                throw new SosException(HttpStatus.BAD_REQUEST,
                        "Tốc độ mô phỏng từ " + (int) MIN_SPEED_KMH + " đến " + (int) MAX_SPEED_KMH + " km/h");
            }
            saveValue(AppSettingKey.SIMULATION_SPEED_KMH, String.valueOf(Math.round(v)), me);
        }
        cache = null;
        log.info("🧪 [DEMO] {} đổi: tự sinh = {}, ẩn = {}", me.email(), generateEnabled(), hidden());
        // Hàng đợi đang mở ở mọi máy: ẩn -> tin STOMP thành INCIDENT_REMOVED, hiện lại -> thẻ quay về
        if (wasHidden != hidden()) {
            incidentRepository.findDemoIds(IncidentStatus.ACTIVE, DemoIncidents.LEGACY_NAMES)
                    .forEach(id -> notifier.notify(id, IncidentEvent.INCIDENT_UPDATED));
        }
        return get();
    }

    /** Xoá hẳn theo từng lô (danh sách IN không quá dài khi còn hàng nghìn ca demo cũ). */
    private static final int PURGE_BATCH = 500;

    @Override
    @Transactional
    public DemoPurgeResponse purge(DemoPurgeScope scope) {
        OfficerPrincipal me = currentOfficer.require();
        if (scope == null) throw new SosException(HttpStatus.BAD_REQUEST, "Chọn phạm vi dọn: OPEN hoặc ALL");
        List<String> ids = incidentRepository.findDemoIds(
                scope == DemoPurgeScope.OPEN ? IncidentStatus.ACTIVE : EnumSet.allOf(IncidentStatus.class),
                DemoIncidents.LEGACY_NAMES);
        long deleted = 0;
        if (!ids.isEmpty()) {
            // ca còn mở: hàng đợi đang mở gỡ thẻ, xe mô phỏng dừng (ca đã đóng không còn trên hàng đợi)
            List<String> open = scope == DemoPurgeScope.OPEN ? ids
                    : incidentRepository.findDemoIds(IncidentStatus.ACTIVE, DemoIncidents.LEGACY_NAMES);
            notifier.notifyDeleted(open);
            if (!open.isEmpty()) incidentDispatchRepository.findIdsByIncidentIds(open).forEach(dispatchSimulationService::stop);
            for (int from = 0; from < ids.size(); from += PURGE_BATCH) {
                deleted += incidentRepository.deleteWithClaims(ids.subList(from, Math.min(ids.size(), from + PURGE_BATCH)));
            }
        }
        log.info("🧹 [DEMO] {} dọn ca demo ({}): xoá {} ca", me.email(), scope, deleted);
        return DemoPurgeResponse.builder().scope(scope).deleted(deleted).settings(get()).build();
    }

    private void save(AppSettingKey key, boolean value, OfficerPrincipal me) {
        saveValue(key, Boolean.toString(value), me);
    }

    private void saveValue(AppSettingKey key, String value, OfficerPrincipal me) {
        appSettingRepository.save(AppSetting.builder()
                .key(key)
                .value(value)
                .updatedAt(LocalDateTime.now())
                .updatedBy(me.memberId())
                .build());
    }

    private boolean flag(AppSettingKey key, boolean fallback) {
        AppSetting row = settings().get(key);
        return row == null ? fallback : Boolean.parseBoolean(row.getValue());
    }

    private Map<AppSettingKey, AppSetting> settings() {
        Map<AppSettingKey, AppSetting> c = cache;
        if (c == null) {
            c = appSettingRepository.findAll().stream()
                    .collect(Collectors.toMap(AppSetting::getKey, s -> s));
            cache = c;
        }
        return c;
    }
}
