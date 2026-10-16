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
import com.sosvietnam.model.payload.request.CreateIncidentRequest;
import com.sosvietnam.model.payload.request.ReleaseIncidentRequest;
import com.sosvietnam.model.payload.response.LocationResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.service.implement.IncidentServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Phân luồng khi tạo ca, nhận / nhả / giao ca trong IncidentServiceImpl (repository giả lập). */
class IncidentRoutingTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentClaimRepository claimRepo = mock(IncidentClaimRepository.class);
    final AccountRepository accountRepo = mock(AccountRepository.class);
    final GeoService geo = mock(GeoService.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final IncidentEscalationService escalation = mock(IncidentEscalationService.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, claimRepo, accountRepo, geo,
            mock(MediaStorageService.class), notifier, escalation);

    final Role officerRole = Role.builder().id(2L).roleName("OFFICER").build();
    final Account caTanAn = officerAccount("congan.tanan@sos.vn", AgencyType.POLICE, UnitLevel.WARD, "Phường Tân An");
    final Account caTanAn2 = officerAccount("congan2.tanan@sos.vn", AgencyType.POLICE, UnitLevel.WARD, "Phường Tân An");

    Account officerAccount(String email, AgencyType agency, UnitLevel level, String ward) {
        return Account.builder().id(UUID.randomUUID()).email(email).firstName("Trực").lastName("Ban").role(officerRole)
                .agencyType(agency).unitLevel(level).province("Cần Thơ").ward(ward).isActive(true).build();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "escalationMinutes", 15L);
        when(incidentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(geo.resolveLocation(anyDouble(), anyDouble())).thenReturn(LocationResponse.builder()
                .province("Cần Thơ").provinceCode("92").ward("Phường Tân An").build());
        when(accountRepo.findActiveOfficers(AgencyType.POLICE, UnitLevel.WARD)).thenReturn(List.of(caTanAn));
    }

    @AfterEach
    void clearAuth() {
        SecurityContextHolder.clearContext();
    }

    Incident create(EmergencyType type) {
        CreateIncidentRequest req = new CreateIncidentRequest();
        req.setEmergencyType(type);
        req.setLatitude(10.03);
        req.setLongitude(105.78);
        service.createIncident(req);
        ArgumentCaptor<Incident> saved = ArgumentCaptor.forClass(Incident.class);
        verify(incidentRepo).save(saved.capture());
        return saved.getValue();
    }

    @Test
    void police_goesToWardWithOfficer_andEscalatesAfter15Minutes() {
        Incident inc = create(EmergencyType.POLICE);
        assertThat(inc.getRoutedAgency()).isEqualTo(AgencyType.POLICE);
        assertThat(inc.getRoutedLevel()).isEqualTo(UnitLevel.WARD);
        assertThat(inc.getEscalateAt()).isEqualTo(inc.getCreatedAt().plusMinutes(15));
        verify(escalation).schedule(inc);
        verify(notifier).notify(inc.getId(), IncidentEvent.INCIDENT_NEW);
    }

    @Test
    void police_wardWithoutOfficer_goesStraightToProvince() {
        when(accountRepo.findActiveOfficers(AgencyType.POLICE, UnitLevel.WARD)).thenReturn(List.of());
        Incident inc = create(EmergencyType.POLICE);
        assertThat(inc.getRoutedLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(inc.getEscalateAt()).isNull();
    }

    @Test
    void traffic_goesToCsgtProvince_andFireToFireProvince_withoutEscalation() {
        Incident traffic = create(EmergencyType.TRAFFIC);
        assertThat(traffic.getRoutedAgency()).isEqualTo(AgencyType.CSGT);
        assertThat(traffic.getRoutedLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(traffic.getEscalateAt()).isNull();

        reset(incidentRepo);
        when(incidentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        Incident fire = create(EmergencyType.FIRE);
        assertThat(fire.getRoutedAgency()).isEqualTo(AgencyType.FIRE);
        assertThat(fire.getRoutedLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(fire.getEscalateAt()).isNull();
    }

    @Test
    void outsideBoundaries_goesToNationalLevel_withNullArea() {
        when(geo.resolveLocation(anyDouble(), anyDouble())).thenReturn(LocationResponse.builder()
                .province("Việt Nam").ward(GeoService.OUTSIDE_BOUNDARIES).build());
        Incident inc = create(EmergencyType.POLICE);
        assertThat(inc.getRoutedLevel()).isEqualTo(UnitLevel.NATIONAL);
        assertThat(inc.getProvince()).isNull();
        assertThat(inc.getWard()).isNull();
    }

    @Test
    void ambulance_isRejected_hospitalsDoNotTakeIncidents() {
        CreateIncidentRequest req = new CreateIncidentRequest();
        req.setEmergencyType(EmergencyType.AMBULANCE);
        req.setLatitude(10.03);
        req.setLongitude(105.78);
        assertThatThrownBy(() -> service.createIncident(req))
                .isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
        verify(incidentRepo, never()).save(any());
    }

    // ------------------------------------------------------------------ nhận / nhả / giao ca

    Incident pendingWardIncident() {
        return Incident.builder().id("SOS-1").routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD)
                .status(IncidentStatus.PENDING).province("Cần Thơ").ward("Phường Tân An")
                .createdAt(LocalDateTime.now()).escalateAt(LocalDateTime.now().plusMinutes(15)).build();
    }

    void loginAs(Account account) {
        when(accountRepo.findByEmail(account.getEmail())).thenReturn(Optional.of(account));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(account.getEmail(), null, "OFFICER"));
    }

    @Test
    void claim_locksTheIncident_andLogsAClaim() {
        loginAs(caTanAn);
        Incident inc = pendingWardIncident();
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(inc));
        when(incidentRepo.claim(eq("SOS-1"), eq(caTanAn.getId()), eq(UnitLevel.WARD), any())).thenReturn(1);
        when(notifier.toQueueViews(any(), any())).thenReturn(List.of(new com.sosvietnam.model.payload.response.IncidentQueueResponse()));

        service.claimIncident("SOS-1");

        verify(claimRepo).save(any(IncidentClaim.class));
        verify(notifier).notify("SOS-1", IncidentEvent.INCIDENT_UPDATED);
    }

    @Test
    void claim_isRefused_whenAlreadyHoldingAnotherIncident_orSomeoneWasFaster() {
        loginAs(caTanAn);
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(pendingWardIncident()));

        when(incidentRepo.existsByHandlerIdAndStatusIn(eq(caTanAn.getId()), any())).thenReturn(true);
        assertThatThrownBy(() -> service.claimIncident("SOS-1")).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);

        when(incidentRepo.existsByHandlerIdAndStatusIn(eq(caTanAn.getId()), any())).thenReturn(false);
        when(incidentRepo.claim(any(), any(), any(), any())).thenReturn(0);
        assertThatThrownBy(() -> service.claimIncident("SOS-1")).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(claimRepo, never()).save(any());
    }

    @Test
    void claim_isRefused_forProvinceOfficerOnWardIncident() {
        Account province = officerAccount("congan.cantho@sos.vn", AgencyType.POLICE, UnitLevel.PROVINCE, null);
        loginAs(province);
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(pendingWardIncident()));
        assertThatThrownBy(() -> service.claimIncident("SOS-1")).extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void release_toQueue_closesClaim_andReschedulesEscalation() {
        loginAs(caTanAn);
        Incident held = pendingWardIncident();
        held.setStatus(IncidentStatus.ACCEPTED);
        held.setHandlerId(caTanAn.getId());
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(held));
        when(incidentRepo.release(eq("SOS-1"), eq(caTanAn.getId()), any())).thenReturn(1);
        IncidentClaim open = IncidentClaim.builder().claimedAt(LocalDateTime.now()).build();
        when(claimRepo.findFirstByIncident_IdAndAccount_IdAndReleasedAtIsNull("SOS-1", caTanAn.getId())).thenReturn(Optional.of(open));
        when(notifier.toQueueViews(any(), any())).thenReturn(List.of(new com.sosvietnam.model.payload.response.IncidentQueueResponse()));

        // Không có transaction thật trong unit test: đăng ký afterCommit cần synchronization đang bật
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        try {
            service.releaseIncident("SOS-1", null);
            org.springframework.transaction.support.TransactionSynchronizationManager.getSynchronizations()
                    .forEach(org.springframework.transaction.support.TransactionSynchronization::afterCommit);
        } finally {
            org.springframework.transaction.support.TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(open.getEndReason()).isEqualTo(ClaimEndReason.RELEASED);
        assertThat(open.getReleasedAt()).isNotNull();
        verify(escalation).schedule(held);
    }

    @Test
    void handOver_onlyToIdleColleagueOfSameUnit() {
        loginAs(caTanAn);
        Incident held = pendingWardIncident();
        held.setStatus(IncidentStatus.ACCEPTED);
        held.setHandlerId(caTanAn.getId());
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(held));
        when(notifier.toQueueViews(any(), any())).thenReturn(List.of(new com.sosvietnam.model.payload.response.IncidentQueueResponse()));

        Account fire = officerAccount("pccc@sos.vn", AgencyType.FIRE, UnitLevel.PROVINCE, null);
        when(accountRepo.findById(fire.getId())).thenReturn(Optional.of(fire));
        ReleaseIncidentRequest toFire = new ReleaseIncidentRequest();
        toFire.setToAccountId(fire.getId());
        assertThatThrownBy(() -> service.releaseIncident("SOS-1", toFire)).extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);

        when(accountRepo.findById(caTanAn2.getId())).thenReturn(Optional.of(caTanAn2));
        ReleaseIncidentRequest toColleague = new ReleaseIncidentRequest();
        toColleague.setToAccountId(caTanAn2.getId());
        when(incidentRepo.existsByHandlerIdAndStatusIn(eq(caTanAn2.getId()), any())).thenReturn(true);
        assertThatThrownBy(() -> service.releaseIncident("SOS-1", toColleague)).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);

        when(incidentRepo.existsByHandlerIdAndStatusIn(eq(caTanAn2.getId()), any())).thenReturn(false);
        when(incidentRepo.handOver("SOS-1", caTanAn.getId(), caTanAn2.getId(), null)).thenReturn(0);
        when(incidentRepo.handOver(eq("SOS-1"), eq(caTanAn.getId()), eq(caTanAn2.getId()), any())).thenReturn(1);
        service.releaseIncident("SOS-1", toColleague);
        verify(claimRepo).save(argThat(c -> c.getAccount() == caTanAn2));
        verify(escalation, never()).schedule(any());
    }

    // ------------------------------------------------------------------ ca do BE cũ tạo + phân trang

    @Test
    void unroutedOldIncidents_getRoutedAndScheduled() {
        Incident old = Incident.builder().id("SOS-OLD").emergencyType(EmergencyType.POLICE).status(IncidentStatus.PENDING)
                .province("Cần Thơ").ward("Phường Tân An").createdAt(LocalDateTime.now().minusMinutes(20)).build();
        Incident oldTraffic = Incident.builder().id("SOS-OLD2").emergencyType(EmergencyType.TRAFFIC).status(IncidentStatus.PENDING)
                .province("Cần Thơ").ward(GeoService.UNKNOWN_WARD).createdAt(LocalDateTime.now()).build();
        when(incidentRepo.findByStatusInAndRoutedAgencyIsNull(any())).thenReturn(List.of(old, oldTraffic));

        assertThat(service.routeUnroutedIncidents()).isEqualTo(2);

        assertThat(old.getRoutedAgency()).isEqualTo(AgencyType.POLICE);
        assertThat(old.getRoutedLevel()).isEqualTo(UnitLevel.WARD);
        // hạn tính từ lúc tạo ca (đã quá hạn -> lịch hẹn sẽ leo thang ngay)
        assertThat(old.getEscalateAt()).isEqualTo(old.getCreatedAt().plusMinutes(15));
        assertThat(oldTraffic.getRoutedAgency()).isEqualTo(AgencyType.CSGT);
        assertThat(oldTraffic.getRoutedLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(oldTraffic.getWard()).isNull();
        verify(escalation).schedule(old);
        verify(notifier).notify("SOS-OLD", IncidentEvent.INCIDENT_NEW);
    }

    @Test
    void queue_isPaged_newestFirst() {
        loginAs(caTanAn);
        List<Incident> five = new java.util.ArrayList<>();
        for (int k = 0; k < 5; k++) {
            Incident inc = pendingWardIncident();
            inc.setId("SOS-" + k);
            five.add(inc);
        }
        when(incidentRepo.findByStatusInAndRoutedAgencyInOrderByCreatedAtDesc(any(), any())).thenReturn(five);
        when(notifier.toQueueViews(any(), any())).thenAnswer(i -> ((java.util.Collection<?>) i.getArgument(0)).stream()
                .map(x -> new com.sosvietnam.model.payload.response.IncidentQueueResponse()).toList());

        var page1 = service.getQueue(1, 2);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat((List<?>) page1.getContent()).hasSize(2);
        assertThat(page1.isLast()).isFalse();
        var last = service.getQueue(2, 2);
        assertThat((List<?>) last.getContent()).hasSize(1);
        assertThat(last.isLast()).isTrue();
        assertThatThrownBy(() -> service.getQueue(0, 0)).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getQueue(0, 101)).isInstanceOf(SosException.class);
    }
}
