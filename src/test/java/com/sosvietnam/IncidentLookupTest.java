package com.sosvietnam;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.response.IncidentLookupResponse;
import com.sosvietnam.model.payload.response.IncidentTrackingResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentMediaRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.service.GeoService;
import com.sosvietnam.service.IncidentEscalationService;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.MediaStorageService;
import com.sosvietnam.service.implement.IncidentServiceImpl;
import com.sosvietnam.util.LookupCodes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Mã tra cứu: sinh/chuẩn hoá, chỉ hiện khi ca hoàn tất, tra cứu cần đúng mã + 4 số cuối SĐT. */
class IncidentLookupTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentDispatchRepository dispatchRepo = mock(IncidentDispatchRepository.class);
    final GeoService geo = mock(GeoService.class);
    final com.sosvietnam.repository.UnitMemberRepository members = mock(com.sosvietnam.repository.UnitMemberRepository.class);
    final com.sosvietnam.service.DemoModeService demoMode = mock(com.sosvietnam.service.DemoModeService.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, mock(IncidentClaimRepository.class),
            mock(IncidentMediaRepository.class), dispatchRepo, mock(RescueStationRepository.class),
            mock(AccountRepository.class), members, new com.sosvietnam.security.CurrentOfficer(), geo, mock(MediaStorageService.class),
            mock(IncidentNotifier.class), mock(IncidentEscalationService.class),
            mock(com.sosvietnam.repository.IncidentReportRepository.class), mock(com.sosvietnam.repository.IncidentRouteRepository.class),
            mock(com.sosvietnam.repository.IncidentDocumentRepository.class), demoMode,
            mock(com.sosvietnam.service.CitizenSmsService.class), new com.sosvietnam.security.LookupAttemptLimiter());

    Incident incident;

    @BeforeEach
    void setUp() {
        incident = Incident.builder().id("SOS-20261018-ABC123").lookupCode("K7M2QX9P").phone("0912345678")
                .emergencyType(EmergencyType.POLICE).status(IncidentStatus.RESOLVED)
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD)
                .province(TestGeo.CAN_THO).ward(TestGeo.TAN_AN).address("02 Nguyễn Tri Phương")
                .createdAt(LocalDateTime.now().minusHours(2)).build();
        when(incidentRepo.findByLookupCode(anyString())).thenReturn(Optional.empty());
        when(incidentRepo.findByLookupCode("K7M2QX9P")).thenReturn(Optional.of(incident));
        when(dispatchRepo.findByIncident_IdOrderByDispatchedAtAsc(anyString())).thenReturn(List.of());
    }

    IncidentLookupRequest req(String code, String last4) {
        return IncidentLookupRequest.builder().lookupCode(code).phoneLast4(last4).build();
    }

    @Test
    void generatedCodes_useReadableAlphabet_andRoundTrip() {
        for (int i = 0; i < 200; i++) {
            String code = LookupCodes.generate();
            assertThat(code).hasSize(8).doesNotContain("0", "O", "1", "I", "L");
            assertThat(LookupCodes.normalize(LookupCodes.format(code).toLowerCase())).isEqualTo(code);
        }
        assertThat(LookupCodes.format("K7M2QX9P")).isEqualTo("K7M2-QX9P");
        assertThat(LookupCodes.normalize("K7M2-QX9")).isNull();
        assertThat(LookupCodes.normalize("K7M2-QX0P")).isNull();
    }

    @Test
    void lookup_withCodeAndLast4_returnsRescueInfoWithoutIdOrPhone() {
        IncidentLookupResponse res = service.lookup(req(" k7m2-qx9p ", "5678"));
        assertThat(res.getLookupCode()).isEqualTo("K7M2-QX9P");
        assertThat(res.getStatus()).isEqualTo(IncidentStatus.RESOLVED);
        assertThat(res.getWard()).isEqualTo("Phường Tân An");
        assertThat(res.getHandlingUnit()).contains("Công An");
        assertThat(res.toString()).doesNotContain("SOS-20261018-ABC123", "0912345678");
        assertThat(res.getPhoneMasked()).isEqualTo("091****678");
        assertThat(res.getFullAddress()).isEqualTo("02 Nguyễn Tri Phương, Phường Tân An, Cần Thơ");
    }

    @Test
    void fullAddress_doesNotRepeatWardOrProvinceAlreadyInAddress() {
        assertThat(IncidentLookupResponse.joinAddress("Phường Tân An, Cần Thơ", "Phường Tân An", "Cần Thơ"))
                .isEqualTo("Phường Tân An, Cần Thơ");
        assertThat(IncidentLookupResponse.joinAddress(null, "Phường Tân An", "Cần Thơ"))
                .isEqualTo("Phường Tân An, Cần Thơ");
        assertThat(IncidentLookupResponse.joinAddress(null, null, null)).isNull();
        assertThat(IncidentLookupResponse.dedupeParts("Phường Tân An, Cần Thơ, Phường Tân An, Cần Thơ"))
                .isEqualTo("Phường Tân An, Cần Thơ");
    }

    @Test
    void wrongPhone_andUnknownCode_giveSameNotFound() {
        assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "0000")))
                .isInstanceOf(SosException.class).hasMessage("Mã tra cứu hoặc số điện thoại không đúng")
                .extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
        assertThatThrownBy(() -> service.lookup(req("ZZZZ-ZZZZ", "5678")))
                .isInstanceOf(SosException.class).hasMessage("Mã tra cứu hoặc số điện thoại không đúng");
        assertThatThrownBy(() -> service.lookup(req("bad", "5678")))
                .isInstanceOf(SosException.class).hasMessage("Mã tra cứu hoặc số điện thoại không đúng");
    }

    com.sosvietnam.model.payload.request.CreateIncidentRequest sosFrom(String name) {
        var create = new com.sosvietnam.model.payload.request.CreateIncidentRequest();
        create.setPhone("0912345678");
        create.setCitizenName(name);
        create.setEmergencyType(EmergencyType.POLICE);
        create.setLatitude(10.0);
        create.setLongitude(105.0);
        return create;
    }

    /** Đã có 1 ca mở: 409 OPEN_CASE_EXISTS (không lộ ca cũ); "Báo sự việc khác" (new_case) thì tạo ca mới. */
    @Test
    void phoneWithOneOpenCase_409Exists_unlessNewCase() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resumeHours", 24L);
        incident.setStatus(IncidentStatus.VERIFYING);
        incident.setCitizenName("Võ Văn Tịnh");
        incident.setCreatedAt(LocalDateTime.now().minusHours(3));
        when(incidentRepo.findByPhoneAndStatusInOrderByCreatedAtDesc("0912345678", IncidentStatus.ACTIVE))
                .thenReturn(List.of(incident));
        when(geo.resolveArea(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(GeoService.Area.NONE);
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> service.createIncident(sosFrom("vo van tinh"))).isInstanceOf(SosException.class)
                .satisfies(e -> {
                    SosException s = (SosException) e;
                    assertThat(s.getHttpStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(s.getData()).containsEntry("code", "OPEN_CASE_EXISTS").containsEntry("open_count", "1");
                    assertThat(s.getMessage()).doesNotContain(incident.getId());
                });

        var create = sosFrom("vo van tinh");
        create.setNewCase(true);
        var res = service.createIncident(create);
        assertThat(res.getIncident().getId()).isNotEqualTo(incident.getId());
        assertThat(res.isResumed()).isFalse();
        assertThat(res.getIncident().getLookupCode()).matches("[A-Z0-9]{4}-[A-Z0-9]{4}"); // mã ca có ngay lúc tạo

        // khai tên thật khác với ca đang mở: vẫn chặn như trước
        var other = sosFrom("Người khác");
        other.setNewCase(true);
        assertThatThrownBy(() -> service.createIncident(other)).isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    /** Đã có 2 ca mở (tính cả Báo hộ): 409 OPEN_CASE_LIMIT, kể cả khi gửi new_case. */
    @Test
    void phoneWithTwoOpenCases_409Limit() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resumeHours", 24L);
        incident.setStatus(IncidentStatus.VERIFYING);
        incident.setCreatedAt(LocalDateTime.now().minusHours(1));
        Incident second = Incident.builder().id("SOS-2").phone("0912345678").status(IncidentStatus.PENDING)
                .reportType(com.sosvietnam.model.payload.enums.ReportType.PROXY)
                .handlerId(java.util.UUID.randomUUID()).createdAt(LocalDateTime.now().minusMinutes(5)).build();
        when(incidentRepo.findByPhoneAndStatusInOrderByCreatedAtDesc("0912345678", IncidentStatus.ACTIVE))
                .thenReturn(List.of(second, incident));
        var create = sosFrom("Võ Văn Tịnh");
        create.setNewCase(true);
        assertThatThrownBy(() -> service.createIncident(create)).isInstanceOf(SosException.class)
                .satisfies(e -> assertThat(((SosException) e).getData()).containsEntry("code", "OPEN_CASE_LIMIT"));
    }

    /** Tra cứu ca còn mở: trả incident + mã truy cập mới (mã cũ hết hiệu lực) để vào lại màn hình gọi. */
    @Test
    void lookupOfAnOpenCase_givesANewAccessToken() {
        incident.setStatus(IncidentStatus.VERIFYING);
        incident.setCitizenTokenHash(com.sosvietnam.util.CitizenTokens.sha256("old"));
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        IncidentLookupResponse res = service.lookup(req("K7M2-QX9P", "5678"));
        assertThat(res.isOpen()).isTrue();
        assertThat(res.getIncident().getId()).isEqualTo(incident.getId());
        assertThat(com.sosvietnam.util.CitizenTokens.matches(incident, res.getCitizenAccessToken())).isTrue();
        assertThat(com.sosvietnam.util.CitizenTokens.matches(incident, "old")).isFalse();

        incident.setStatus(IncidentStatus.RESOLVED); // ca đã đóng: chỉ xem, không có mã truy cập
        IncidentLookupResponse closed = service.lookup(req("K7M2-QX9P", "5678"));
        assertThat(closed.isOpen()).isFalse();
        assertThat(closed.getCitizenAccessToken()).isNull();
        assertThat(closed.getIncident()).isNull();
    }

    /** Sai 5 lần / 10 phút / mã ca -> 429 (kể cả khi lần 6 nhập đúng); đúng trước đó thì xoá đếm. */
    @Test
    void fiveWrongTries_lockTheCode() {
        for (int i = 0; i < 4; i++) {
            assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "0000"))).extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
        }
        service.lookup(req("k7m2qx9p", "5678")); // đúng: xoá đếm
        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "1111"))).extracting("httpStatus").isEqualTo(HttpStatus.NOT_FOUND);
        }
        assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "5678"))).isInstanceOf(SosException.class)
                .hasMessageContaining("Nhập sai quá nhiều lần").extracting("httpStatus").isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    void openSosOlderThanResumeWindow_doesNotBlock() {
        org.springframework.test.util.ReflectionTestUtils.setField(service, "resumeHours", 24L);
        incident.setStatus(IncidentStatus.RESOLVING);
        incident.setCreatedAt(LocalDateTime.now().minusDays(3));
        when(geo.resolveArea(org.mockito.ArgumentMatchers.anyDouble(), org.mockito.ArgumentMatchers.anyDouble())).thenReturn(GeoService.Area.NONE);
        when(incidentRepo.findByPhoneAndStatusInOrderByCreatedAtDesc("0912345678", IncidentStatus.ACTIVE))
                .thenReturn(List.of(incident));
        when(incidentRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var res = service.createIncident(sosFrom("Võ Văn Tịnh"));
        assertThat(res.isResumed()).isFalse();
        assertThat(res.getIncident().getId()).isNotEqualTo(incident.getId());
    }

    @Test
    void tagEmoji_isStrippedBeforeSaving() {
        assertThat(IncidentServiceImpl.stripEmoji("🚨 Bị cướp giật / Móc túi")).isEqualTo("Bị cướp giật / Móc túi");
        assertThat(IncidentServiceImpl.stripEmoji("⚔️ Bạo lực / Gây rối trật tự")).isEqualTo("Bạo lực / Gây rối trật tự");
        assertThat(IncidentServiceImpl.stripEmoji("👁️ Bị theo dõi / Đe dọa")).isEqualTo("Bị theo dõi / Đe dọa");
        assertThat(IncidentServiceImpl.stripEmoji("Đánh nhau, gây rối")).isEqualTo("Đánh nhau, gây rối");
    }

    @Test
    void incidentWithoutPhone_cannotBeLookedUp() {
        incident.setPhone(null);
        assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "5678"))).isInstanceOf(SosException.class);
    }

    @Test
    void tracking_showsTheCaseCodeAtEveryStatus() {
        for (IncidentStatus s : IncidentStatus.values()) {
            incident.setStatus(s);
            assertThat(IncidentTrackingResponse.from(incident, null).getLookupCode()).as(s.name()).isEqualTo("K7M2-QX9P");
        }
    }
}
