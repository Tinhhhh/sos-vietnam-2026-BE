package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.ClaimEndReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentEvent;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.CancelIncidentRequest;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.service.implement.IncidentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Người dân huỷ tin báo + hệ thống tự huỷ ca chờ quá lâu (IncidentServiceImpl, repository giả lập). */
class IncidentCancelTest {

    static final String TOKEN = "citizen-token";

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentClaimRepository claimRepo = mock(IncidentClaimRepository.class);
    final AccountRepository accountRepo = mock(AccountRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final com.sosvietnam.repository.UnitMemberRepository members = mock(com.sosvietnam.repository.UnitMemberRepository.class);
    final com.sosvietnam.service.DemoModeService demoMode = mock(com.sosvietnam.service.DemoModeService.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, claimRepo, mock(com.sosvietnam.repository.IncidentMediaRepository.class), mock(com.sosvietnam.repository.IncidentDispatchRepository.class), mock(com.sosvietnam.repository.RescueStationRepository.class), accountRepo, members, new com.sosvietnam.security.CurrentOfficer(),
            mock(GeoService.class), mock(MediaStorageService.class), notifier, mock(IncidentEscalationService.class),
            mock(com.sosvietnam.repository.IncidentReportRepository.class), mock(com.sosvietnam.repository.IncidentRouteRepository.class),
            mock(com.sosvietnam.repository.IncidentDocumentRepository.class), demoMode,
            mock(com.sosvietnam.service.CitizenSmsService.class), new com.sosvietnam.security.LookupAttemptLimiter());

    Incident incident;

    @BeforeEach
    void setUp() throws Exception {
        ReflectionTestUtils.setField(service, "autoCancelMinutes", 60L);
        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(TOKEN.getBytes(StandardCharsets.UTF_8)));
        incident = Incident.builder().id("SOS-1").emergencyType(EmergencyType.POLICE).status(IncidentStatus.PENDING)
                .citizenTokenHash(hash).createdAt(LocalDateTime.now().minusMinutes(5)).build();
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(incident));
    }

    CancelIncidentRequest reason(CancelReason reason) {
        CancelIncidentRequest req = new CancelIncidentRequest();
        req.setReason(reason);
        return req;
    }

    @Test
    void citizenCancel_openIncident_cancelsAndRemovesFromQueue() {
        when(incidentRepo.cancel(eq("SOS-1"), eq(CancelReason.MISTAKE), eq(IncidentStatus.ACTIVE), any()))
                .thenAnswer(i -> {
                    incident.setStatus(IncidentStatus.CANCELLED);
                    incident.setCancelReason(CancelReason.MISTAKE);
                    return 1;
                });

        IncidentTrackingResponse res = service.cancelByCitizen("SOS-1", TOKEN, reason(CancelReason.MISTAKE));

        assertThat(res.getStatus()).isEqualTo(IncidentStatus.CANCELLED);
        assertThat(res.getCancelReasonLabel()).isEqualTo("Bấm nhầm");
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_REMOVED);
    }

    @Test
    void citizenCancel_heldIncident_closesTheOfficersClaim() {
        UUID officerId = UUID.randomUUID();
        IncidentClaim claim = IncidentClaim.builder().claimedAt(LocalDateTime.now().minusMinutes(2)).build();
        when(incidentRepo.cancel(eq("SOS-1"), isNull(), eq(IncidentStatus.ACTIVE), any())).thenAnswer(i -> {
            incident.setStatus(IncidentStatus.CANCELLED);
            incident.setHandlerId(officerId);
            return 1;
        });
        when(claimRepo.findFirstByIncident_IdAndMember_IdAndReleasedAtIsNull("SOS-1", officerId))
                .thenReturn(Optional.of(claim));
        when(members.findById(officerId)).thenReturn(Optional.of(
                TestGeo.member(Account.builder().id(officerId).firstName("Văn").lastName("A").build())));

        IncidentTrackingResponse res = service.cancelByCitizen("SOS-1", TOKEN, null); // no reason given

        assertThat(claim.getReleasedAt()).isNotNull();
        assertThat(claim.getEndReason()).isEqualTo(ClaimEndReason.CLOSED);
        assertThat(res.getHandler()).isNotNull();
    }

    @Test
    void citizenCancel_alreadyClosed_is409() {
        when(incidentRepo.cancel(any(), any(), any(), any())).thenReturn(0);

        assertThatThrownBy(() -> service.cancelByCitizen("SOS-1", TOKEN, reason(CancelReason.OTHER)))
                .isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(notifier, never()).notify(any(), any());
    }

    @Test
    void citizenCancel_systemOnlyReason_is400() {
        assertThatThrownBy(() -> service.cancelByCitizen("SOS-1", TOKEN, reason(CancelReason.NO_RESPONDER)))
                .isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(incidentRepo, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void citizenCancel_wrongToken_is403() {
        assertThatThrownBy(() -> service.cancelByCitizen("SOS-1", "someone-else", null))
                .isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
        verify(incidentRepo, never()).cancel(any(), any(), any(), any());
    }

    @Test
    void staleIncidents_overAnHourWithNoResponder_areCancelledWithNoResponderReason() {
        Incident old1 = Incident.builder().id("OLD-1").status(IncidentStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(90)).build();
        Incident old2 = Incident.builder().id("OLD-2").status(IncidentStatus.PENDING)
                .createdAt(LocalDateTime.now().minusMinutes(61)).build();
        when(incidentRepo.findByStatusAndHandlerIdIsNullAndCreatedAtBefore(eq(IncidentStatus.PENDING), any()))
                .thenReturn(List.of(old1, old2));
        when(incidentRepo.findById("OLD-1")).thenReturn(Optional.of(old1));
        when(incidentRepo.findById("OLD-2")).thenReturn(Optional.of(old2));
        when(incidentRepo.cancel(eq("OLD-1"), any(), any(), any())).thenReturn(1);
        when(incidentRepo.cancel(eq("OLD-2"), any(), any(), any())).thenReturn(0); // claimed meanwhile

        int cancelled = service.cancelStaleIncidents();

        assertThat(cancelled).isEqualTo(1);
        // Cut-off = now - 60 min; only incidents still waiting (PENDING) may be cancelled
        ArgumentCaptor<LocalDateTime> before = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(incidentRepo).findByStatusAndHandlerIdIsNullAndCreatedAtBefore(eq(IncidentStatus.PENDING), before.capture());
        assertThat(before.getValue()).isBetween(LocalDateTime.now().minusMinutes(61), LocalDateTime.now().minusMinutes(59));
        verify(incidentRepo).cancel(eq("OLD-1"), eq(CancelReason.NO_RESPONDER), eq(Set.of(IncidentStatus.PENDING)), any());
        verify(notifier).notify("OLD-1", IncidentEvent.INCIDENT_REMOVED);
        verify(notifier, never()).notify(eq("OLD-2"), any());
    }
}
