package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.OfficerServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** GET /officers/status: same unit only, online from STOMP, busy from held incidents, sorted. */
class OfficerStatusTest {

    final AccountRepository accounts = mock(AccountRepository.class);
    final IncidentRepository incidents = mock(IncidentRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final OfficerServiceImpl service = new OfficerServiceImpl(accounts, incidents, notifier);
    final Role officerRole = Role.builder().id(2L).roleName("OFFICER").build();

    Account officer(String email, String first, String ward) {
        return Account.builder().id(UUID.randomUUID()).email(email).firstName(first).lastName("Cán Bộ").role(officerRole)
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(TestGeo.ward(ward)).isActive(true).build();
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void sameWardOnly_onlineAndBusy_sortedFreeOnlineFirst() {
        Account me = officer("me@sos.vn", "An", "Phường Tân An");
        Account free = officer("free@sos.vn", "Bình", "Phường Tân An");
        Account busy = officer("busy@sos.vn", "Cường", "Phường Tân An");
        Account offline = officer("off@sos.vn", "Dũng", "Phường Tân An");
        Account otherWard = officer("other@sos.vn", "Em", "Phường Cái Khế");
        when(accounts.findByEmail("me@sos.vn")).thenReturn(Optional.of(me));
        when(accounts.findActiveOfficers(AgencyType.POLICE, UnitLevel.WARD))
                .thenReturn(List.of(offline, busy, me, free, otherWard));
        when(incidents.findByHandlerIdInAndStatusIn(any(), any())).thenReturn(List.of(
                Incident.builder().id("SOS-9").handlerId(busy.getId()).status(IncidentStatus.VERIFYING)
                        .emergencyType(EmergencyType.POLICE).build()));
        when(notifier.onlineEmails()).thenReturn(Set.of("me@sos.vn", "free@sos.vn", "busy@sos.vn", "other@sos.vn"));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("me@sos.vn", null, "OFFICER"));

        List<OfficerStatusResponse> list = service.getUnitOfficers();

        assertThat(list).extracting(OfficerStatusResponse::getEmail)
                .containsExactly("me@sos.vn", "free@sos.vn", "busy@sos.vn", "off@sos.vn"); // other ward not listed
        assertThat(list.get(0).isMe()).isTrue();
        OfficerStatusResponse b = list.get(2);
        assertThat(b.isOnline()).isTrue();
        assertThat(b.isBusy()).isTrue();
        assertThat(b.getHeldIncident().getId()).isEqualTo("SOS-9");
        assertThat(b.getUnitLabel()).contains("Công An");
        assertThat(list.get(3).isOnline()).isFalse();
    }
}
