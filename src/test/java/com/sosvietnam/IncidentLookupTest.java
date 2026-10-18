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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Mã tra cứu: sinh/chuẩn hoá, chỉ hiện khi ca hoàn tất, tra cứu cần đúng mã + 4 số cuối SĐT. */
class IncidentLookupTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentDispatchRepository dispatchRepo = mock(IncidentDispatchRepository.class);
    final IncidentServiceImpl service = new IncidentServiceImpl(incidentRepo, mock(IncidentClaimRepository.class),
            mock(IncidentMediaRepository.class), dispatchRepo, mock(RescueStationRepository.class),
            mock(AccountRepository.class), mock(GeoService.class), mock(MediaStorageService.class),
            mock(IncidentNotifier.class), mock(IncidentEscalationService.class));

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

    @Test
    void incidentWithoutPhone_cannotBeLookedUp() {
        incident.setPhone(null);
        assertThatThrownBy(() -> service.lookup(req("K7M2-QX9P", "5678"))).isInstanceOf(SosException.class);
    }

    @Test
    void tracking_showsCodeOnlyWhenResolved() {
        assertThat(IncidentTrackingResponse.from(incident, null).getLookupCode()).isEqualTo("K7M2-QX9P");
        for (IncidentStatus s : List.of(IncidentStatus.PENDING, IncidentStatus.VERIFYING, IncidentStatus.RESOLVING,
                IncidentStatus.CANCELLED)) {
            incident.setStatus(s);
            assertThat(IncidentTrackingResponse.from(incident, null).getLookupCode()).as(s.name()).isNull();
        }
    }
}
