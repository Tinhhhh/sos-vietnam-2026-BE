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
    final com.sosvietnam.repository.IncidentDispatchRepository dispatchRepo = mock(com.sosvietnam.repository.IncidentDispatchRepository.class);
    final GeoService geo = mock(GeoService.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final IncidentEscalationService escalation = mock(IncidentEscalationService.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, claimRepo, mock(com.sosvietnam.repository.IncidentMediaRepository.class), dispatchRepo, mock(com.sosvietnam.repository.RescueStationRepository.class), accountRepo, geo,
            mock(MediaStorageService.class), notifier, escalation,
            mock(com.sosvietnam.repository.IncidentReportRepository.class), mock(com.sosvietnam.repository.IncidentRouteRepository.class),
            mock(com.sosvietnam.repository.IncidentDocumentRepository.class));

    final Role officerRole = Role.builder().id(2L).roleName("OFFICER").build();
    final Account caTanAn = officerAccount("congan.tanan@sos.vn", AgencyType.POLICE, UnitLevel.WARD, "Phường Tân An");
    final Account caTanAn2 = officerAccount("congan2.tanan@sos.vn", AgencyType.POLICE, UnitLevel.WARD, "Phường Tân An");

    Account officerAccount(String email, AgencyType agency, UnitLevel level, String ward) {
        return Account.builder().id(UUID.randomUUID()).email(email).firstName("Trực").lastName("Ban").role(officerRole)
                .agencyType(agency).unitLevel(level).province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward(ward)).isActive(true).build();
    }

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "escalationMinutes", 15L);
        when(incidentRepo.save(any())).thenAnswer(i -> i.getArgument(0));
        when(geo.resolveArea(anyDouble(), anyDouble())).thenReturn(new GeoService.Area(TestGeo.CAN_THO, TestGeo.TAN_AN));
        when(accountRepo.existsByAgencyTypeAndUnitLevelAndWard_IdAndIsActiveTrue(AgencyType.POLICE, UnitLevel.WARD,
                TestGeo.TAN_AN.getId())).thenReturn(true);
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
        when(accountRepo.existsByAgencyTypeAndUnitLevelAndWard_IdAndIsActiveTrue(any(), any(), any())).thenReturn(false);
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
        when(geo.resolveArea(anyDouble(), anyDouble())).thenReturn(GeoService.Area.NONE);
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
                .status(IncidentStatus.PENDING).province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An"))
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
        held.setStatus(IncidentStatus.VERIFYING);
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
        held.setStatus(IncidentStatus.VERIFYING);
        held.setHandlerId(caTanAn.getId());
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(held));
        when(notifier.toQueueViews(any(), any())).thenReturn(List.of(new com.sosvietnam.model.payload.response.IncidentQueueResponse()));

        Account fire = officerAccount("pccc@sos.vn", AgencyType.FIRE, UnitLevel.PROVINCE, null);
        when(accountRepo.findById(fire.getId())).thenReturn(Optional.of(fire));
        ReleaseIncidentRequest toFire = new ReleaseIncidentRequest();
        toFire.setToAccountId(fire.getId());
        toFire.setNote("Hết ca trực, giao lại");
        assertThatThrownBy(() -> service.releaseIncident("SOS-1", toFire)).extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);

        // Giao ca bắt buộc lý do
        when(accountRepo.findById(caTanAn2.getId())).thenReturn(Optional.of(caTanAn2));
        ReleaseIncidentRequest noReason = new ReleaseIncidentRequest();
        noReason.setToAccountId(caTanAn2.getId());
        assertThatThrownBy(() -> service.releaseIncident("SOS-1", noReason))
                .isInstanceOf(SosException.class).hasMessageContaining("lý do giao ca");

        ReleaseIncidentRequest toColleague = new ReleaseIncidentRequest();
        toColleague.setToAccountId(caTanAn2.getId());
        toColleague.setNote("Cần đi hiện trường khác");
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
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward("Phường Tân An")).createdAt(LocalDateTime.now().minusMinutes(20)).build();
        Incident oldTraffic = Incident.builder().id("SOS-OLD2").emergencyType(EmergencyType.TRAFFIC).status(IncidentStatus.PENDING)
                .province(TestGeo.province("Cần Thơ")).ward(TestGeo.ward(GeoService.UNKNOWN_WARD)).createdAt(LocalDateTime.now()).build();
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

        var page1 = service.getQueue(1, 2, null);
        assertThat(page1.getTotalElements()).isEqualTo(5);
        assertThat(page1.getTotalPages()).isEqualTo(3);
        assertThat((List<?>) page1.getContent()).hasSize(2);
        assertThat(page1.isLast()).isFalse();
        var last = service.getQueue(2, 2, null);
        assertThat((List<?>) last.getContent()).hasSize(1);
        assertThat(last.isLast()).isTrue();
        assertThatThrownBy(() -> service.getQueue(0, 0, null)).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getQueue(0, 101, null)).isInstanceOf(SosException.class);
    }

    @Test
    void queue_daysFilter_usesTheSinceQuery_andRejectsBadRange() {
        loginAs(caTanAn);
        when(incidentRepo.findByStatusInAndRoutedAgencyInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(pendingWardIncident()));
        when(notifier.toQueueViews(any(), any())).thenReturn(List.of());

        var page = service.getQueue(0, 4, 7);
        assertThat(page.getTotalElements()).isEqualTo(1);
        verify(incidentRepo).findByStatusInAndRoutedAgencyInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq(IncidentStatus.ACTIVE), eq(List.of(AgencyType.POLICE)),
                argThat(since -> since.isBefore(LocalDateTime.now().minusDays(7).plusMinutes(1))
                        && since.isAfter(LocalDateTime.now().minusDays(7).minusMinutes(1))));
        assertThatThrownBy(() -> service.getQueue(0, 4, 0)).isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getQueue(0, 4, 91)).isInstanceOf(SosException.class);
    }

    @Test
    void history_closedIncidentsInMyArea_pagedWithHandlerAndDispatchCount() {
        loginAs(caTanAn);
        Incident resolved = pendingWardIncident();
        resolved.setId("SOS-R");
        resolved.setStatus(IncidentStatus.RESOLVED);
        resolved.setCitizenName("Nguyễn Văn Báo");
        Incident cancelled = pendingWardIncident();
        cancelled.setId("SOS-C");
        cancelled.setStatus(IncidentStatus.CANCELLED);
        cancelled.setCancelReason(com.sosvietnam.model.payload.enums.CancelReason.MISTAKE);
        cancelled.setCancelledAt(LocalDateTime.now().minusMinutes(5));
        Incident otherWard = pendingWardIncident();
        otherWard.setId("SOS-X");
        otherWard.setStatus(IncidentStatus.CANCELLED);
        otherWard.setWard(TestGeo.ward("Phường Cái Khế"));
        when(incidentRepo.findByStatusInAndRoutedAgencyInAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                eq(IncidentStatus.CLOSED), any(), any())).thenReturn(List.of(resolved, cancelled, otherWard));
        when(claimRepo.findFirstByIncident_IdOrderByClaimedAtDesc("SOS-R"))
                .thenReturn(Optional.of(IncidentClaim.builder().account(caTanAn).claimedAt(LocalDateTime.now()).build()));
        when(claimRepo.findFirstByIncident_IdOrderByClaimedAtDesc("SOS-C")).thenReturn(Optional.empty());
        when(dispatchRepo.countByIncident_Id("SOS-R")).thenReturn(2L);

        var page = service.getHistory(null, null, null, 0, 4);
        assertThat(page.getTotalElements()).isEqualTo(2); // other ward is not visible to a ward officer
        @SuppressWarnings("unchecked")
        var items = (List<com.sosvietnam.model.payload.response.IncidentHistoryResponse>) page.getContent();
        assertThat(items).extracting("id").containsExactly("SOS-R", "SOS-C");
        assertThat(items.get(0).getHandlerName()).isEqualTo(caTanAn.fullName());
        assertThat(items.get(0).getDispatchCount()).isEqualTo(2);
        assertThat(items.get(1).getCancelReasonLabel()).isNotBlank();
        assertThat(items.get(1).getClosedAt()).isNotNull();

        // search ignores accents and case
        var found = service.getHistory(7, "nguyen van bao", null, 0, 4);
        assertThat(found.getTotalElements()).isEqualTo(1);
        assertThat(service.getHistory(7, "sos-c", null, 0, 4).getTotalElements()).isEqualTo(1);
        assertThatThrownBy(() -> service.getHistory(31 * 3, null, null, 0, 4)).isInstanceOf(SosException.class);
    }

    @Test
    void detail_inMyWard_hasClaims_otherWard_isForbidden() {
        loginAs(caTanAn);
        Incident mine = pendingWardIncident();
        mine.setIncidentTag("Trộm cắp");
        when(incidentRepo.findById("SOS-1")).thenReturn(Optional.of(mine));
        when(claimRepo.findByIncident_IdOrderByClaimedAtAsc("SOS-1")).thenReturn(List.of(
                IncidentClaim.builder().account(caTanAn).claimedAt(LocalDateTime.now().minusMinutes(3))
                        .releasedAt(LocalDateTime.now()).endReason(com.sosvietnam.model.payload.enums.ClaimEndReason.RELEASED).build()));

        var detail = service.getDetail("SOS-1");
        assertThat(detail.getIncidentTags()).containsExactly("Trộm cắp");
        assertThat(detail.getClaims()).hasSize(1);
        assertThat(detail.getClaims().get(0).getOfficerName()).isEqualTo(caTanAn.fullName());
        assertThat(detail.getClaims().get(0).getEndReasonLabel()).isNotBlank();
        assertThat(detail.getMedia()).isEmpty();

        Incident other = pendingWardIncident();
        other.setId("SOS-2");
        other.setWard(TestGeo.ward("Phường Cái Khế"));
        when(incidentRepo.findById("SOS-2")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> service.getDetail("SOS-2"))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }
}
