package com.sosvietnam;

import com.sosvietnam.model.entity.AppSetting;
import com.sosvietnam.model.payload.enums.AppSettingKey;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.request.DemoSettingsRequest;
import com.sosvietnam.repository.AppSettingRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.UnitMemberRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.DemoModeServiceImpl;
import com.sosvietnam.util.DemoIncidents;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Công tắc ca demo: mặc định theo .env, lưu app_settings, đổi "ẩn" thì đẩy lại các ca demo đang mở. */
class DemoModeServiceTest {

    final AppSettingRepository settingRepo = mock(AppSettingRepository.class);
    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final CurrentOfficer currentOfficer = mock(CurrentOfficer.class);
    final List<AppSetting> rows = new ArrayList<>();

    DemoModeServiceImpl service(boolean envDefault) {
        doAnswer(i -> List.copyOf(rows)).when(settingRepo).findAll();
        doAnswer(i -> {
            AppSetting s = i.getArgument(0);
            rows.removeIf(r -> r.getKey() == s.getKey());
            rows.add(s);
            return s;
        }).when(settingRepo).save(any());
        OfficerPrincipal me = mock(OfficerPrincipal.class);
        when(me.memberId()).thenReturn(UUID.randomUUID());
        when(me.email()).thenReturn("congan.tanan@sos.vn");
        when(currentOfficer.require()).thenReturn(me);
        return new DemoModeServiceImpl(settingRepo, incidentRepo, mock(UnitMemberRepository.class), currentOfficer,
                notifier, envDefault);
    }

    @Test
    void noRow_followsEnv_andNotHidden() {
        assertThat(service(true).generateEnabled()).isTrue();
        assertThat(service(false).generateEnabled()).isFalse();
        assertThat(service(true).hidden()).isFalse();
    }

    @Test
    void update_savesAndWinsOverEnv_hidingRepushesOpenDemoIncidents() {
        when(incidentRepo.findDemoIds(any(), any())).thenReturn(List.of("SOS-D1", "SOS-D2"));
        DemoModeServiceImpl service = service(true);

        DemoSettingsRequest off = new DemoSettingsRequest();
        off.setGenerate(false);
        var res = service.update(off);
        assertThat(res.isGenerate()).isFalse();
        assertThat(res.isHidden()).isFalse();
        assertThat(rows).extracting(AppSetting::getKey).containsExactly(AppSettingKey.DEMO_GENERATE);
        verify(notifier, never()).notify(any(), any()); // "ẩn" không đổi

        DemoSettingsRequest hide = new DemoSettingsRequest();
        hide.setHidden(true);
        res = service.update(hide);
        assertThat(res.isHidden()).isTrue();
        assertThat(res.isGenerate()).isFalse(); // trường không gửi thì giữ nguyên
        assertThat(res.getOpenCount()).isEqualTo(2);
        verify(notifier).notify("SOS-D1", IncidentEvent.INCIDENT_UPDATED);
        verify(notifier).notify("SOS-D2", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void demoIncidents_recognisedByMarkOrLegacyName() {
        assertThat(DemoIncidents.isDemo("[DEMO] Trần Văn Demo (tỉnh)")).isTrue();
        assertThat(DemoIncidents.isDemo("Lê Văn Demo (CSGT)")).isTrue();
        assertThat(DemoIncidents.isDemo("Nguyễn Văn Demo")).isFalse();
        assertThat(DemoIncidents.isDemo((String) null)).isFalse();
    }
}
