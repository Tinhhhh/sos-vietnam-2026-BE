package com.sosvietnam;

import com.sosvietnam.model.payload.enums.CancelReason;
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
    final IncidentEscalationService escalation = mock(IncidentEscalationService.class);
    final com.sosvietnam.repository.UnitMemberRepository members = mock(com.sosvietnam.repository.UnitMemberRepository.class);
    final com.sosvietnam.service.DemoModeService demoMode = mock(com.sosvietnam.service.DemoModeService.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, claimRepo, mock(com.sosvietnam.repository.IncidentMediaRepository.class), mock(com.sosvietnam.repository.IncidentDispatchRepository.class), mock(com.sosvietnam.repository.RescueStationRepository.class), accountRepo, members, new com.sosvietnam.security.CurrentOfficer(),
            mock(GeoService.class), mock(MediaStorageService.class), notifier, escalation,
            mock(com.sosvietnam.repository.IncidentReportRepository.class), mock(com.sosvietnam.repository.IncidentRouteRepository.class),
            mock(com.sosvietnam.repository.IncidentDocumentRepository.class), demoMode);

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
        TestGeo.signIn(officer);
    }

    @Test
    void transfer_movesHeldCaseToTheOtherForceQueue() {
        loginAsOfficer();
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        IncidentClaim claim = IncidentClaim.builder().claimedAt(LocalDateTime.now().minusMinutes(2)).build();
        when(claimRepo.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull("SOS-1", officer.getId()))
                .thenReturn(Optional.of(claim));
        var req = com.sosvietnam.model.payload.request.TransferIncidentRequest.builder()
                .agency(AgencyType.FIRE).reason("Xác minh là cháy nhà, không phải trộm").build();

        service.transferAgency("SOS-1", req);

        assertThat(incident.getRoutedAgency()).isEqualTo(AgencyType.FIRE);
        assertThat(incident.getEmergencyType()).isEqualTo(EmergencyType.FIRE);
        assertThat(incident.getRoutedLevel()).isEqualTo(UnitLevel.PROVINCE); // PCCC nhận ca ở cấp phòng
        assertThat(incident.getStatus()).isEqualTo(IncidentStatus.PENDING);
        assertThat(incident.getHandlerId()).isNull();
        assertThat(incident.getEscalateAt()).isNull();
        assertThat(incident.getTransferredFrom()).isEqualTo(AgencyType.POLICE);
        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.TRANSFERRED);
        verify(notifier).notifyRemovedFor("SOS-1", AgencyType.POLICE);
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_NEW);
    }

    @Test
    void transfer_needsReason_andOnlyWhileVerifying() {
        loginAsOfficer();
        var noReason = com.sosvietnam.model.payload.request.TransferIncidentRequest.builder().agency(AgencyType.FIRE).reason("ab").build();
        assertThatThrownBy(() -> service.transferAgency("SOS-1", noReason)).hasMessageContaining("lý do");
        var same = com.sosvietnam.model.payload.request.TransferIncidentRequest.builder().agency(AgencyType.POLICE).reason("Giữ nguyên lực lượng").build();
        assertThatThrownBy(() -> service.transferAgency("SOS-1", same)).hasMessageContaining("lực lượng này rồi");
        incident.setStatus(IncidentStatus.RESOLVING);
        var ok = com.sosvietnam.model.payload.request.TransferIncidentRequest.builder().agency(AgencyType.FIRE).reason("Xác minh là cháy nhà").build();
        assertThatThrownBy(() -> service.transferAgency("SOS-1", ok)).isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void autoRelease_putsCaseBackAndClosesClaim() {
        when(incidentRepo.release(eq("SOS-1"), eq(officer.getId()), any())).thenReturn(1);
        IncidentClaim claim = IncidentClaim.builder().claimedAt(LocalDateTime.now().minusMinutes(9)).build();
        when(claimRepo.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull("SOS-1", officer.getId()))
                .thenReturn(Optional.of(claim));
        assertThat(service.autoRelease("SOS-1", officer.getId(), "mất kết nối")).isTrue();
        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.AUTO_RELEASED);
        verify(escalation).schedule(incident);
        when(incidentRepo.release(eq("SOS-1"), eq(officer.getId()), any())).thenReturn(0);
        assertThat(service.autoRelease("SOS-1", officer.getId(), "mất kết nối")).isFalse();
    }

    @Test
    void media_afterCloseOrBeforeAnyoneTookIt_isRejected() {
        var file = new org.springframework.mock.web.MockMultipartFile("files", "a.jpg", "image/jpeg", new byte[10]);
        incident.setStatus(IncidentStatus.CANCELLED);
        assertThatThrownBy(() -> service.addMedia("SOS-1", "tok", java.util.List.of(file)))
                .hasMessageContaining("đã kết thúc");
        incident.setStatus(IncidentStatus.PENDING);
        incident.setHandlerId(null);
        incident.setCreatedAt(LocalDateTime.now().minusMinutes(30));
        assertThatThrownBy(() -> service.addMedia("SOS-1", "tok", java.util.List.of(file)))
                .hasMessageContaining("khi đã có cán bộ tiếp nhận");
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
        when(claimRepo.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull("SOS-1", officer.getId()))
                .thenReturn(Optional.of(claim));
        when(incidentRepo.escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(IncidentStatus.HELD), any())).thenReturn(1);

        service.escalateNow("SOS-1", null, "Vượt khả năng cấp xã"); // before the 15-minute deadline: the officer may still do it

        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.ESCALATED);
        assertThat(claim.getNote()).isEqualTo("Vượt khả năng cấp xã");
        assertThat(incident.getEscalationReason()).isEqualTo(com.sosvietnam.model.payload.enums.EscalationReason.OFFICER_REQUEST);
        assertThat(incident.getEscalationNote()).isEqualTo("Vượt khả năng cấp xã");
        assertThat(incident.getEscalatedBy()).isEqualTo(officer.getId());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_ESCALATED);
    }

    @Test
    void officerEscalates_needsAReason() {
        loginAsOfficer();
        assertThatThrownBy(() -> service.escalateNow("SOS-1", null, " ab "))
                .isInstanceOf(SosException.class).hasMessageContaining("lý do");
        verify(incidentRepo, never()).escalateHeld(any(), any(), any(), any());
    }

    @Test
    void citizen_cannotEscalate_beforeDeadline() {
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "tok", null))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(incidentRepo, never()).escalateHeld(any(), any(), any(), any());
    }

    @Test
    void citizen_cannotEscalate_onceForcesAreDispatched() {
        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "tok", null))
                .isInstanceOf(SosException.class)
                .hasMessageContaining("đã được điều động");
    }

    @Test
    void citizen_escalates_whenOfficerHeldItPastDeadlineWithoutDispatch() {
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        when(incidentRepo.escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(Set.of(IncidentStatus.VERIFYING)), any()))
                .thenReturn(1);

        service.escalateNow("SOS-1", "tok", null);

        assertThat(incident.getEscalationReason()).isEqualTo(com.sosvietnam.model.payload.enums.EscalationReason.CITIZEN_REQUEST);
        verify(incidentRepo).escalateHeld(eq("SOS-1"), eq(officer.getId()), eq(Set.of(IncidentStatus.VERIFYING)), any());
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_ESCALATED);
    }

    @Test
    void citizen_escalates_pendingIncidentNobodyTookPastDeadline() {
        incident.setStatus(IncidentStatus.PENDING);
        incident.setHandlerId(null);
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        when(escalation.escalateIfDue("SOS-1")).thenReturn(true);

        service.escalateNow("SOS-1", "tok", null);

        verify(escalation).escalateIfDue("SOS-1");
        verify(incidentRepo, never()).escalateHeld(any(), any(), any(), any());
    }

    @Test
    void officerCancels_heldIncident_anyStep_closesClaimWithReason() {
        loginAsOfficer();
        IncidentClaim claim = IncidentClaim.builder().claimedAt(LocalDateTime.now().minusMinutes(3)).build();
        when(claimRepo.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull("SOS-1", officer.getId()))
                .thenReturn(Optional.of(claim));
        when(incidentRepo.cancel(eq("SOS-1"), eq(CancelReason.OFFICER_CANCELLED), eq(IncidentStatus.HELD), any())).thenReturn(1);

        service.cancelByOfficer("SOS-1", "Người báo xác nhận báo nhầm");

        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.CLOSED);
        assertThat(claim.getNote()).contains("báo nhầm");
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_REMOVED);
    }

    @Test
    void officerCancel_notHolder_isForbidden() {
        loginAsOfficer();
        incident.setHandlerId(UUID.randomUUID());
        assertThatThrownBy(() -> service.cancelByOfficer("SOS-1", "Lý do bất kỳ"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void escalate_provinceIncident_isRejected() {
        loginAsOfficer();
        incident.setRoutedLevel(UnitLevel.PROVINCE);
        assertThatThrownBy(() -> service.escalateNow("SOS-1", null, "Vượt khả năng cấp xã"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void citizen_wrongToken_isForbidden() {
        incident.setEscalateAt(LocalDateTime.now().minusMinutes(1));
        assertThatThrownBy(() -> service.escalateNow("SOS-1", "bad", null))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
    }
}
