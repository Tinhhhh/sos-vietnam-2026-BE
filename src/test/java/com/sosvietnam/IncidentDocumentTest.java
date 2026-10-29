package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.IncidentDocument;
import com.sosvietnam.model.entity.IncidentReport;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CitizenSignChannel;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.ReportResult;
import com.sosvietnam.model.payload.enums.SignatureType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.IncidentDocumentRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.SignDocumentRequest;
import com.sosvietnam.model.payload.response.IncidentDocumentResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentDocumentRepository;
import com.sosvietnam.repository.IncidentReportRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.service.implement.IncidentDocumentServiceImpl;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.IncidentDocumentDocx;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Văn bản báo cáo: nháp tự điền, sửa xoá chữ ký, ký hộ cần lý do + uỷ quyền, dân ký qua mã tra cứu, khoá khi đủ chữ ký. */
class IncidentDocumentTest {

    final IncidentRepository incidentRepo = mock(IncidentRepository.class);
    final IncidentClaimRepository claimRepo = mock(IncidentClaimRepository.class);
    final IncidentDispatchRepository dispatchRepo = mock(IncidentDispatchRepository.class);
    final IncidentReportRepository reportRepo = mock(IncidentReportRepository.class);
    final IncidentDocumentRepository docRepo = mock(IncidentDocumentRepository.class);
    final com.sosvietnam.repository.UnitMemberRepository members = mock(com.sosvietnam.repository.UnitMemberRepository.class);
    final IncidentDocumentServiceImpl service = new IncidentDocumentServiceImpl(incidentRepo,
            new com.sosvietnam.security.CurrentOfficer(), claimRepo, dispatchRepo, reportRepo, docRepo, members,
            mock(com.sosvietnam.service.IncidentNotifier.class), new com.sosvietnam.security.LookupAttemptLimiter());

    final Map<String, IncidentDocument> store = new HashMap<>();
    Account officer;
    Incident incident;
    static final String TOKEN = "citizen-token";

    @BeforeEach
    void setUp() {
        officer = Account.builder().id(UUID.randomUUID()).email("congan.tanan@sos.vn").firstName("Tân An")
                .lastName("Cán Bộ Công An").role(Role.builder().id(2L).roleName("OFFICER").build())
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.CAN_THO).ward(TestGeo.TAN_AN)
                .address("02 Nguyễn Tri Phương").isActive(true).build();
        incident = Incident.builder().id("SOS-20261021-ABC123").lookupCode("K7M2QX9P").phone("0912345678")
                .citizenName("Võ Văn Tịnh").emergencyType(EmergencyType.POLICE).status(IncidentStatus.RESOLVED)
                .routedAgency(AgencyType.POLICE).routedLevel(UnitLevel.WARD).escalateAt(LocalDateTime.now())
                .province(TestGeo.CAN_THO).ward(TestGeo.TAN_AN).address("Phường Tân An, Cần Thơ")
                .incidentTag("Trộm cắp đột nhập").description("Có người lạ vào nhà")
                .latitude(10.0276).longitude(105.7574).citizenTokenHash(CitizenTokens.sha256(TOKEN))
                .createdAt(LocalDateTime.of(2026, 10, 21, 9, 29)).updatedAt(LocalDateTime.of(2026, 10, 21, 9, 40)).build();

        when(incidentRepo.findById(incident.getId())).thenReturn(Optional.of(incident));
        when(incidentRepo.findByLookupCode(anyString())).thenReturn(Optional.empty());
        when(incidentRepo.findByLookupCode("K7M2QX9P")).thenReturn(Optional.of(incident));
        when(members.findById(officer.getId())).thenReturn(Optional.of(TestGeo.member(officer)));
        when(claimRepo.findFirstByIncident_IdOrderByClaimedAtDesc(anyString())).thenReturn(Optional.of(
                IncidentClaim.builder().incident(incident).member(TestGeo.member(officer)).claimedAt(incident.getCreatedAt()).build()));
        when(dispatchRepo.findByIncident_IdOrderByDispatchedAtAsc(anyString())).thenReturn(List.of());
        when(reportRepo.findById(anyString())).thenReturn(Optional.of(IncidentReport.builder()
                .incidentId(incident.getId()).result(ReportResult.RESOLVED).summary("Đã bắt giữ đối tượng")
                .forces("3 cán bộ, 1 xe").createdAt(LocalDateTime.of(2026, 10, 21, 9, 40)).build()));
        when(docRepo.findById(anyString())).thenAnswer(inv -> Optional.ofNullable(store.get(inv.<String>getArgument(0))));
        when(docRepo.save(any())).thenAnswer(inv -> {
            IncidentDocument d = inv.getArgument(0);
            store.put(d.getIncidentId(), d);
            return d;
        });
        TestGeo.signIn(officer);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    SignDocumentRequest typed(String name) {
        return SignDocumentRequest.builder().name(name).type(SignatureType.TYPE).build();
    }

    @Test
    void draft_isFilledFromIncident_withOfficialHeader() {
        IncidentDocumentResponse res = service.get(incident.getId());
        assertThat(res.isSaved()).isFalse();
        assertThat(res.isEditable()).isTrue();
        assertThat(res.getDocNumber()).isEqualTo("SOS-20261021-ABC123/PTN-SC");
        IncidentDocumentRequest f = res.getFields();
        assertThat(f.getHeaderUpper()).isEqualTo("CÔNG AN TP. CẦN THƠ");
        assertThat(f.getHeaderLower()).isEqualTo("CÔNG AN PHƯỜNG TÂN AN");
        assertThat(f.getPlaceDate()).isEqualTo("Cần Thơ, ngày 21 tháng 10 năm 2026");
        assertThat(f.getOfficerName()).isEqualTo("Cán Bộ Công An Tân An");
        assertThat(f.getUnitName()).isEqualTo("Công An Phường Tân An");
        assertThat(f.getReporterPhone()).isEqualTo("0912345678");
        assertThat(f.getIncidentAddress()).isEqualTo("Phường Tân An, Cần Thơ");
        assertThat(f.getDescription()).isEqualTo("Trộm cắp đột nhập. Có người lạ vào nhà");
        assertThat(f.getResolutionResult()).contains("Đã xử lý xong: Đã bắt giữ đối tượng", "3 cán bộ, 1 xe");
    }

    @Test
    void draft_officerIsTheDelegatedOfficer_whenTheProvinceDelegated() {
        // tỉnh nhận ca rồi uỷ quyền xuống phường: văn bản điền cán bộ phường (người xử lý), không phải người nhận ca
        Account province = Account.builder().id(UUID.randomUUID()).email("congan.cantho@sos.vn").firstName("Cần Thơ")
                .lastName("Chỉ Huy Tỉnh").role(Role.builder().id(2L).roleName("OFFICER").build())
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.PROVINCE).province(TestGeo.CAN_THO).isActive(true).build();
        when(claimRepo.findFirstByIncident_IdOrderByClaimedAtDesc(anyString())).thenReturn(Optional.of(
                IncidentClaim.builder().incident(incident).member(TestGeo.member(province)).claimedAt(incident.getCreatedAt()).build()));
        com.sosvietnam.model.entity.RescueStation station =
                com.sosvietnam.model.entity.RescueStation.builder().name("Công an phường Tân An").build();
        when(dispatchRepo.findByIncident_IdOrderByDispatchedAtAsc(anyString())).thenReturn(List.of(
                com.sosvietnam.model.entity.IncidentDispatch.builder().station(station)
                        .role(com.sosvietnam.model.payload.enums.DispatchRole.PRIMARY)
                        .status(com.sosvietnam.model.payload.enums.DispatchStatus.DONE)
                        .assignedTo(TestGeo.member(officer)).build()));

        IncidentDocumentRequest f = service.get(incident.getId()).getFields();
        assertThat(f.getOfficerName()).isEqualTo("Cán Bộ Công An Tân An");
        assertThat(f.getUnitName()).isEqualTo("Công An Phường Tân An");
    }

    @Test
    void onlyResolvedIncidents_haveADocument() {
        incident.setStatus(IncidentStatus.CANCELLED);
        assertThatThrownBy(() -> service.get(incident.getId())).isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void editingSignedContent_clearsSignatures_butSameContentKeepsThem() {
        IncidentDocumentRequest fields = service.get(incident.getId()).getFields();
        service.save(incident.getId(), fields);
        service.signOfficer(incident.getId(), typed("Cán Bộ Công An Tân An"));
        assertThat(store.get(incident.getId()).officerSigned()).isTrue();

        // Lưu lại đúng nội dung cũ (thêm khoảng trắng): chữ ký vẫn còn
        fields.setAdviceGiven(null);
        fields.setOfficerTitle(" Cán bộ tiếp nhận ");
        service.save(incident.getId(), fields);
        assertThat(store.get(incident.getId()).officerSigned()).isTrue();

        fields.setAdviceGiven("Giữ bình tĩnh, khoá cửa");
        IncidentDocumentResponse res = service.save(incident.getId(), fields);
        assertThat(res.getOfficerSignature()).isNull();
    }

    @Test
    void proxySign_needsReasonAndAuthorization() {
        service.save(incident.getId(), service.get(incident.getId()).getFields());
        SignDocumentRequest req = typed("Võ Văn Tịnh");
        assertThatThrownBy(() -> service.signProxy(incident.getId(), req)).isInstanceOf(SosException.class)
                .hasMessageContaining("lý do");
        req.setReason("Người dân bị thương ở tay");
        assertThatThrownBy(() -> service.signProxy(incident.getId(), req)).isInstanceOf(SosException.class)
                .hasMessageContaining("uỷ quyền");
        req.setAuthorized(true);
        IncidentDocumentResponse res = service.signProxy(incident.getId(), req);
        assertThat(res.getCitizenSignature().getChannel()).isEqualTo(CitizenSignChannel.OFFICER_PROXY);
        assertThat(res.getCitizenSignature().getSignedByName()).isEqualTo("Cán Bộ Công An Tân An");
        assertThat(res.getCitizenSignature().getProxyReason()).isEqualTo("Người dân bị thương ở tay");
    }

    @Test
    void citizenSignsViaLookup_thenBothSigned_locksTheDocument() {
        // not saved yet: the citizen already reads the auto-filled draft
        assertThat(service.lookupGet(IncidentLookupRequest.builder().lookupCode("K7M2-QX9P")
                .phoneLast4("5678").build()).isSaved()).isFalse();

        service.save(incident.getId(), service.get(incident.getId()).getFields());
        IncidentDocumentResponse seen = service.lookupGet(IncidentLookupRequest.builder().lookupCode("k7m2-qx9p")
                .phoneLast4("5678").build());
        assertThat(seen.isEditable()).isFalse();
        assertThat(seen.getFields().getReporterPhone()).isEqualTo("091****678");

        SignDocumentRequest wrong = typed("Võ Văn Tịnh");
        wrong.setLookupCode("K7M2-QX9P");
        wrong.setPhoneLast4("0000");
        assertThatThrownBy(() -> service.lookupSign(wrong)).isInstanceOf(SosException.class);

        SignDocumentRequest sign = SignDocumentRequest.builder().name("Võ Văn Tịnh").type(SignatureType.DRAW)
                .signatureData("data:image/png;base64,iVBORw0KGgo=").lookupCode("K7M2-QX9P").phoneLast4("5678").build();
        assertThat(service.lookupSign(sign).getCitizenSignature().getChannel()).isEqualTo(CitizenSignChannel.CITIZEN);

        IncidentDocumentResponse done = service.signOfficer(incident.getId(), typed("Cán Bộ Công An Tân An"));
        assertThat(done.isLocked()).isTrue();
        assertThat(done.isEditable()).isFalse();
        assertThatThrownBy(() -> service.save(incident.getId(), done.getFields()))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void citizenToken_isChecked() {
        service.save(incident.getId(), service.get(incident.getId()).getFields());
        assertThatThrownBy(() -> service.citizenGet(incident.getId(), "wrong")).isInstanceOf(SosException.class)
                .extracting("httpStatus").isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(service.citizenSign(incident.getId(), TOKEN, typed("Võ Văn Tịnh")).getCitizenSignature()).isNotNull();
    }

    @Test
    void citizen_seesTheDraftBeforeOfficersSaveIt_butCannotSignYet() {
        IncidentDocumentResponse draft = service.citizenGet(incident.getId(), TOKEN);
        assertThat(draft.isSaved()).isFalse();
        assertThat(draft.getFields()).isNotNull();
        assertThatThrownBy(() -> service.citizenSign(incident.getId(), TOKEN, typed("Võ Văn Tịnh")))
                .isInstanceOf(SosException.class);
    }

    @Test
    void drawSignature_needsImage() {
        service.save(incident.getId(), service.get(incident.getId()).getFields());
        SignDocumentRequest req = SignDocumentRequest.builder().name("A").type(SignatureType.DRAW).build();
        assertThatThrownBy(() -> service.signOfficer(incident.getId(), req)).hasMessageContaining("vẽ tay");
    }

    @Test
    void docx_containsHeaderSectionsAndSignatureNames() throws Exception {
        service.save(incident.getId(), service.get(incident.getId()).getFields());
        service.signOfficer(incident.getId(), typed("Cán Bộ Công An Tân An"));
        byte[] file = service.docx(incident.getId());
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(file))) {
            String body = doc.getParagraphs().stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));
            String tables = doc.getTables().stream().map(t -> t.getText()).collect(Collectors.joining("\n"));
            assertThat(body).contains("PHIẾU THÔNG BÁO VÀ TIẾP NHẬN THÔNG TIN", "A. THÔNG TIN CHUNG",
                    "B. THÔNG TIN ĐẾN", "Võ Văn Tịnh", "0912345678", "Đã bắt giữ đối tượng");
            assertThat(tables).contains("CÔNG AN TP. CẦN THƠ", "CÔNG AN PHƯỜNG TÂN AN", "Số: SOS-20261021-ABC123/PTN-SC",
                    "Độc lập - Tự do - Hạnh phúc", "(Chữ ký điện tử: Cán Bộ Công An Tân An)");
        }
        assertThat(IncidentDocumentDocx.build(store.get(incident.getId()), null, id -> null)).isNotEmpty();
    }
}
