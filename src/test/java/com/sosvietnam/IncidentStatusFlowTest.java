package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.ClaimEndReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.service.implement.IncidentServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PENDING -> VERIFYING (nhận ca) -> RESOLVING (yêu cầu điều động) và chuyển ngay lên cấp tỉnh
 * (cán bộ tự chuyển, hoặc người dân yêu cầu khi cán bộ nhận quá 15 phút mà chưa điều động).
 */
class IncidentStatusFlowTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentClaimRepository claimRepo = mock(IncidentClaimRepository.class);
    final AccountRepository accountRepo = mock(AccountRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, claimRepo, mock(com.sosvietnam.repository.IncidentMediaRepository.class), mock(com.sosvietnam.repository.IncidentDispatchRepository.class), mock(com.sosvietnam.repository.RescueStationRepository.class), accountRepo,
            mock(GeoService.class), mock(MediaStorageService.class), notifier, mock(IncidentEscalationService.class));

    final Account officer = Account.builder().id(UUID.randomUUID()).email("congan.tanan@sos.vn").firstName("Cán")
            .lastName("Bộ").role(Role.builder().id(2L).roleName("OFFICER").build()).agencyType(AgencyType.POLICE)
            .unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).isActive(true).build();
    Incident incident;

    @BeforeEach
    void setUp() {
        incident = Incident.builder().id("SOS-1").emergencyType(EmergencyType.POLICE).status(IncidentStatus.VERIFYING)
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An"))
                .handlerId(officer.getId()).citizenTokenHash(CitizenTokens.sha256("tok"))
                .createdAt(LocalDateTime.now().minusMinutes(5)).escalateAt(LocalDateTime.now().plusMinutes(10)).build();
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(incident));
        when(accountRepo.findById(officer.getId())).thenReturn(Optional.of(officer));
        when(notifier.toQueueViews(any(), any())).thenReturn(java.util.List.of(
                com.sosvietnam.model.payload.response.IncidentQueueResponse.builder().id("SOS-1").build()));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    void loginAsOfficer() {
        when(accountRepo.findByEmail(officer.getEmail())).thenReturn(Optional.of(officer));
        SecurityContextHolder.getContext().setAuthentication(
                new TestingAuthenticationToken(officer.getEmail(), null, "OFFICER"));
    }

    @Test
    void requestDispatch_movesVerifyingToResolving() {
        loginAsOfficer();
        when(incidentRepo.startResolving(eq("SOS-1"), eq(officer.getId()), any())).thenReturn(1);

        service.requestDispatch("SOS-1");

        verify(incidentRepo).startResolving(eq("SOS-1"), eq(officer.getId()), any());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void requestDispatch_notHolder_isForbidden() {
        loginAsOfficer();
        incident.setHandlerId(UUID.randomUUID());
        assertThatThrownBy(() -> service.requestDispatch("SOS-1"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void officerEscalates_heldWardIncident_goesToProvinceAndClosesClaim() {
        loginAsOfficer();
        IncidentClaim claim = IncidentClaim.builder().claimedAt(LocalDateTime.now().minusMinutes(3)).build();
        when(claimRepo.findFirstByIncident_IdAndAccount_IdAndReleasedAtIsNull("SOS-1", officer.getId()))
                .thenReturn(Optional.of(claim));
        when(incidentRepo.escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(IncidentStatus.HELD), any())).thenReturn(1);

        service.escalateNow("SOS-1", null); // before the 15-minute deadline: the officer may still do it

        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.ESCALATED);
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_ESCALATED);
    }

    @Test
    void citizen_cannotEscalate_beforeDeadline() {
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "tok"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(incidentRepo, never()).escalateHeld(any(), any(), any(), any());
    }

    @Test
    void citizen_cannotEscalate_onceForcesAreDispatched() {
        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "tok"))
                .isInstanceOf(SosException.class)
                .hasMessageContaining("đã được điều động");
    }

    @Test
    void citizen_escalates_whenOfficerHeldItPastDeadlineWithoutDispatch() {
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        when(incidentRepo.escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(Set.of(IncidentStatus.VERIFYING)), any()))
                .thenReturn(1);

        service.escalateNow("SOS-1", "tok");

        verify(incidentRepo).escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(Set.of(IncidentStatus.VERIFYING)), any());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_ESCALATED);
    }

    @Test
    void escalate_provinceIncident_isRejected() {
        loginAsOfficer();
        incident.setRoutedLevel(UnitLevel.PROVINCE);
        assertThatThrownBy(() -> service.escalateNow("SOS-1", null))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void citizen_wrongToken_isForbidden() {
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "bad"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
    }
}
