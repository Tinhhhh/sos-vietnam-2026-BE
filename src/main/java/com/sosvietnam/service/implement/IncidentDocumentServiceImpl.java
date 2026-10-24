package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentClaim;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.IncidentDocument;
import com.sosvietnam.model.entity.IncidentReport;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CitizenSignChannel;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.SignatureType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.IncidentDocumentRequest;
import com.sosvietnam.model.payload.request.IncidentLookupRequest;
import com.sosvietnam.model.payload.request.SignDocumentRequest;
import com.sosvietnam.model.payload.response.IncidentDocumentResponse;
import com.sosvietnam.model.payload.response.IncidentLookupResponse;
import com.sosvietnam.repository.IncidentClaimRepository;
import com.sosvietnam.repository.IncidentDispatchRepository;
import com.sosvietnam.repository.IncidentDocumentRepository;
import com.sosvietnam.repository.IncidentReportRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.IncidentDocumentService;
import com.sosvietnam.util.AreaNames;
import com.sosvietnam.util.CitizenTokens;
import com.sosvietnam.util.IncidentAccess;
import com.sosvietnam.util.IncidentDocumentDocx;
import com.sosvietnam.util.LookupCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentDocumentServiceImpl implements IncidentDocumentService {

    /** 6 thành phố trực thuộc trung ương (sau sắp xếp 2025): tiêu đề "TP. ..." thay vì "TỈNH ..." */
    private static final Set<String> CITIES = Set.of("ha noi", "ho chi minh", "hai phong", "da nang", "can tho", "hue");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy");
    private static final DateTimeFormatter HOUR = DateTimeFormatter.ofPattern("HH:mm");
    private static final int MIN_PROXY_REASON = 5;

    private final IncidentRepository incidentRepository;
    private final com.sosvietnam.security.CurrentOfficer currentOfficer;
    private final IncidentClaimRepository incidentClaimRepository;
    private final IncidentDispatchRepository incidentDispatchRepository;
    private final IncidentReportRepository incidentReportRepository;
    private final IncidentDocumentRepository documentRepository;
    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final com.sosvietnam.service.IncidentNotifier notifier;

    // ---------------------------------------------------------------- cán bộ

    @Override
    @Transactional(readOnly = true)
    public IncidentDocumentResponse get(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = officerIncident(me, incidentId);
        return documentRepository.findById(incidentId)
                .map(d -> view(d, true, incident, true))
                .orElseGet(() -> view(draft(incident), false, incident, true));
    }

    @Override
    @Transactional
    public IncidentDocumentResponse save(String incidentId, IncidentDocumentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = officerIncident(me, incidentId);
        LocalDateTime now = LocalDateTime.now();
        IncidentDocument doc = documentRepository.findById(incidentId).orElse(null);
        if (doc == null) {
            doc = draft(incident);
            doc.setCreatedBy(me.memberId());
            doc.setCreatedAt(now);
        } else if (doc.locked()) {
            throw new SosException(HttpStatus.CONFLICT, "Văn bản đã đủ chữ ký 2 bên, không sửa được nữa");
        } else if ((doc.officerSigned() || doc.citizenSigned()) && changes(doc, request)) {
            // Chữ ký chỉ có giá trị với nội dung lúc ký
            doc.clearSignatures();
            log.info("📝 [DOCUMENT] {} content changed by {}: signatures cleared", incidentId, me.email());
        }
        apply(doc, request);
        doc.setUpdatedBy(me.memberId());
        doc.setUpdatedAt(now);
        return view(saved(doc), true, incident, true);
    }

    @Override
    @Transactional
    public IncidentDocumentResponse signOfficer(String incidentId, SignDocumentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = officerIncident(me, incidentId);
        IncidentDocument doc = savedOpenDocument(incidentId);
        validateSignature(request);
        LocalDateTime now = LocalDateTime.now();
        doc.setOfficerSignName(request.getName().trim());
        doc.setOfficerSignType(request.getType());
        doc.setOfficerSignature(request.getType() == SignatureType.DRAW ? request.getSignatureData() : null);
        doc.setOfficerSignedBy(me.memberId());
        doc.setOfficerSignedAt(now);
        doc.setUpdatedBy(me.memberId());
        doc.setUpdatedAt(now);
        log.info("✍️ [DOCUMENT] {} signed by officer {}", incidentId, me.email());
        return view(saved(doc), true, incident, true);
    }

    @Override
    @Transactional
    public IncidentDocumentResponse signProxy(String incidentId, SignDocumentRequest request) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = officerIncident(me, incidentId);
        IncidentDocument doc = savedOpenDocument(incidentId);
        if (doc.citizenSigned()) {
            throw new SosException(HttpStatus.CONFLICT, "Người dân đã ký văn bản này");
        }
        String reason = request.getReason() == null ? "" : request.getReason().trim();
        if (reason.length() < MIN_PROXY_REASON) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Ghi lý do ký hộ (ít nhất " + MIN_PROXY_REASON + " ký tự)");
        }
        if (!request.isAuthorized()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Cần xác nhận đã được người dân uỷ quyền ký thay");
        }
        validateSignature(request);
        LocalDateTime now = LocalDateTime.now();
        setCitizenSignature(doc, request, now);
        doc.setCitizenSignChannel(CitizenSignChannel.OFFICER_PROXY);
        doc.setCitizenProxyBy(me.memberId());
        doc.setCitizenProxyReason(reason);
        doc.setUpdatedBy(me.memberId());
        doc.setUpdatedAt(now);
        log.info("✍️ [DOCUMENT] {} citizen box signed by proxy {} ({})", incidentId, me.email(), reason);
        return view(saved(doc), true, incident, true);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] docx(String incidentId) {
        OfficerPrincipal me = currentOfficer();
        Incident incident = officerIncident(me, incidentId);
        IncidentDocument doc = documentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Ca này chưa lập văn bản báo cáo"));
        return IncidentDocumentDocx.build(doc, gmapsUrl(incident), nameLookup());
    }

    // ---------------------------------------------------------------- người dân

    @Override
    @Transactional(readOnly = true)
    public IncidentDocumentResponse citizenGet(String incidentId, String citizenToken) {
        Incident incident = citizenIncident(incidentId, citizenToken);
        return citizenView(citizenDocument(incidentId), incident);
    }

    @Override
    @Transactional
    public IncidentDocumentResponse citizenSign(String incidentId, String citizenToken, SignDocumentRequest request) {
        Incident incident = citizenIncident(incidentId, citizenToken);
        return citizenSignOn(incident, request);
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentDocumentResponse lookupGet(IncidentLookupRequest request) {
        Incident incident = lookupIncident(request.getLookupCode(), request.getPhoneLast4());
        return citizenView(citizenDocument(incident.getId()), incident);
    }

    @Override
    @Transactional
    public IncidentDocumentResponse lookupSign(SignDocumentRequest request) {
        Incident incident = lookupIncident(request.getLookupCode(), request.getPhoneLast4());
        return citizenSignOn(incident, request);
    }

    private IncidentDocumentResponse citizenSignOn(Incident incident, SignDocumentRequest request) {
        IncidentDocument doc = citizenDocument(incident.getId());
        if (doc.locked()) throw new SosException(HttpStatus.CONFLICT, "Văn bản đã đủ chữ ký 2 bên");
        if (doc.citizenSigned()) throw new SosException(HttpStatus.CONFLICT, "Ô người dân đã có chữ ký");
        validateSignature(request);
        LocalDateTime now = LocalDateTime.now();
        setCitizenSignature(doc, request, now);
        doc.setCitizenSignChannel(CitizenSignChannel.CITIZEN);
        doc.setCitizenProxyBy(null);
        doc.setCitizenProxyReason(null);
        doc.setUpdatedAt(now);
        log.info("✍️ [DOCUMENT] {} signed by the citizen", incident.getId());
        return citizenView(saved(doc), incident);
    }

    // ---------------------------------------------------------------- nháp tự điền

    /** Bản nháp điền từ dữ liệu ca: đơn vị = người nhận ca cuối, kết quả = văn bản kết thúc ca (incident_reports). */
    IncidentDocument draft(Incident incident) {
        IncidentClaim claim = incidentClaimRepository.findFirstByIncident_IdOrderByClaimedAtDesc(incident.getId()).orElse(null);
        com.sosvietnam.model.entity.UnitMember member = claim == null ? null : claim.getMember();
        Account officer = member == null ? null : member.getAccount();
        List<IncidentDispatch> dispatches = incidentDispatchRepository.findByIncident_IdOrderByDispatchedAtAsc(incident.getId());
        IncidentReport report = incidentReportRepository.findById(incident.getId()).orElse(null);

        AgencyType agency = officer != null && officer.getAgencyType() != null ? officer.getAgencyType() : incident.getRoutedAgency();
        UnitLevel level = officer != null && officer.getUnitLevel() != null ? officer.getUnitLevel() : incident.getRoutedLevel();
        String province = officer != null && officer.provinceName() != null ? officer.provinceName() : incident.provinceName();
        String ward = officer != null && officer.wardName() != null ? officer.wardName() : incident.wardName();
        String[] header = header(agency, level, province, ward);
        LocalDateTime closedAt = report != null ? report.getCreatedAt()
                : incident.getUpdatedAt() != null ? incident.getUpdatedAt() : LocalDateTime.now();

        String tags = String.join(", ", incident.tags());
        String description = joinNonBlank(". ", tags.isBlank() ? null : tags, incident.getDescription());

        return IncidentDocument.builder()
                .incidentId(incident.getId())
                .docNumber(incident.getId() + "/PTN-SC")
                .headerUpper(header[0])
                .headerLower(header[1])
                .placeDate(placeDate(province, closedAt))
                .officerName(member == null ? null : member.displayName())
                .officerTitle("Cán bộ tiếp nhận")
                .unitName(unitName(agency, level, province, ward))
                .unitAddress(officer == null ? null : blankToNull(officer.fullAddress()))
                .unitPhone(unitPhone(dispatches))
                .reporterName(incident.getCitizenName())
                .reporterPhone(incident.getPhone())
                .targetType("Người dân")
                .incidentTime(incident.getCreatedAt() == null ? null : incident.getCreatedAt().format(TIME))
                .incidentAddress(IncidentLookupResponse.joinAddress(incident.getAddress(), incident.wardName(), incident.provinceName()))
                .coordinates(incident.getLatitude() == null || incident.getLongitude() == null ? null
                        : String.format(Locale.ROOT, "%.5f, %.5f", incident.getLatitude(), incident.getLongitude()))
                .description(description)
                .affectsCitizens(true)
                .needsHelp(true)
                .helpRequest(helpRequest(dispatches))
                .resolutionResult(report == null ? null : joinNonBlank(". ",
                        report.getResult() == null ? null : report.getResult().getLabel()
                                + (report.getSummary() == null ? "" : ": " + report.getSummary()),
                        report.getForces() == null || report.getForces().isBlank() ? null
                                : "Lực lượng, phương tiện tham gia: " + report.getForces()))
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }

    /** Tiêu đề 2 dòng theo Nghị định 30/2020: [cơ quan cấp trên, cơ quan ban hành] */
    static String[] header(AgencyType agency, UnitLevel level, String province, String ward) {
        String prov = provinceTitle(province).toUpperCase(Locale.ROOT);
        if (agency == null || level == UnitLevel.NATIONAL) {
            return new String[]{"BỘ CÔNG AN", "TRUNG TÂM CHỈ HUY"};
        }
        return switch (agency) {
            case CSGT -> new String[]{"CÔNG AN " + prov, "PHÒNG CẢNH SÁT GIAO THÔNG"};
            case FIRE -> new String[]{"CÔNG AN " + prov,
                    level == UnitLevel.WARD && ward != null ? "ĐỘI PCCC VÀ CNCH " + ward.toUpperCase(Locale.ROOT)
                            : "PHÒNG CẢNH SÁT PCCC VÀ CNCH"};
            case HOSPITAL -> new String[]{"SỞ Y TẾ " + prov, "TRUNG TÂM CẤP CỨU 115"};
            default -> level == UnitLevel.WARD && ward != null
                    ? new String[]{"CÔNG AN " + prov, "CÔNG AN " + ward.toUpperCase(Locale.ROOT)}
                    : new String[]{"BỘ CÔNG AN", "CÔNG AN " + prov};
        };
    }

    /** "Cần Thơ" -> "TP. Cần Thơ", "An Giang" -> "Tỉnh An Giang" */
    static String provinceTitle(String province) {
        if (province == null || province.isBlank()) return "";
        String bare = province.trim().replaceFirst("^(?i)(TP\\.?|Thành phố|Tỉnh)\\s+", "");
        return (CITIES.contains(AreaNames.provinceKey(bare)) ? "TP. " : "Tỉnh ") + bare;
    }

    static String placeDate(String province, LocalDateTime at) {
        String bare = province == null ? "" : province.trim().replaceFirst("^(?i)(TP\\.?|Thành phố|Tỉnh)\\s+", "");
        if ("ho chi minh".equals(AreaNames.provinceKey(bare))) bare = "TP. Hồ Chí Minh";
        String date = String.format("ngày %02d tháng %02d năm %d", at.getDayOfMonth(), at.getMonthValue(), at.getYear());
        return bare.isBlank() ? date.substring(0, 1).toUpperCase(Locale.ROOT) + date.substring(1) : bare + ", " + date;
    }

    private static String unitName(AgencyType agency, UnitLevel level, String province, String ward) {
        if (agency == null) return null;
        if (level == UnitLevel.WARD && ward != null) return agency.getLabel() + " " + ward;
        if (level == UnitLevel.PROVINCE && province != null) return agency.getLabel() + " " + provinceTitle(province);
        return agency.getLabel();
    }

    /** SĐT trạm của lượt chủ trì khi đơn vị tự đi (không uỷ quyền) */
    private static String unitPhone(List<IncidentDispatch> dispatches) {
        return dispatches.stream()
                .filter(d -> d.getRole() == DispatchRole.PRIMARY && d.getAssignedTo() == null)
                .map(d -> d.getStation().getPhone())
                .filter(p -> p != null && !p.isBlank())
                .reduce((a, b) -> b).orElse(null);
    }

    private static String helpRequest(List<IncidentDispatch> dispatches) {
        List<String> lines = new ArrayList<>();
        for (IncidentDispatch d : dispatches) {
            StringBuilder s = new StringBuilder("Điều động ").append(d.getStation().getName());
            if (d.getRole() != null) s.append(" (").append(d.getRole().getLabel().toLowerCase(Locale.ROOT)).append(")");
            if (d.getAssignedTo() != null) s.append(", uỷ quyền cho ").append(d.getAssignedTo().displayName());
            if (d.getDepartedAt() != null) s.append(", xuất phát lúc ").append(d.getDepartedAt().format(HOUR));
            if (d.getArrivedAt() != null) s.append(", có mặt lúc ").append(d.getArrivedAt().format(HOUR));
            lines.add(s.toString());
        }
        return lines.isEmpty() ? null : String.join("; ", lines) + ".";
    }

    // ---------------------------------------------------------------- helpers

    /** Lưu + báo màn theo dõi của người dân (trạng thái văn bản đổi) */
    private IncidentDocument saved(IncidentDocument doc) {
        IncidentDocument d = documentRepository.save(doc);
        notifier.notifyCitizen(d.getIncidentId());
        return d;
    }

    private IncidentDocumentResponse view(IncidentDocument doc, boolean saved, Incident incident, boolean officer) {
        return IncidentDocumentResponse.from(doc, saved, officer, incident.getLatitude(), incident.getLongitude(), nameLookup());
    }

    /** Người dân: không sửa được, SĐT chỉ hiện bản che, không lộ toạ độ */
    private IncidentDocumentResponse citizenView(IncidentDocument doc, Incident incident) {
        IncidentDocumentResponse res = IncidentDocumentResponse.from(doc, true, false, null, null, nameLookup());
        res.getFields().setReporterPhone(IncidentLookupResponse.maskPhone(doc.getReporterPhone()));
        res.getFields().setCoordinates(null);
        return res;
    }

    private Function<UUID, String> nameLookup() {
        Map<UUID, String> cache = new HashMap<>();
        return id -> id == null ? null : cache.computeIfAbsent(id,
                k -> unitMemberRepository.findById(k).map(com.sosvietnam.model.entity.UnitMember::displayName).orElse(null));
    }

    private static void apply(IncidentDocument d, IncidentDocumentRequest r) {
        d.setHeaderUpper(trim(r.getHeaderUpper()));
        d.setHeaderLower(trim(r.getHeaderLower()));
        d.setPlaceDate(trim(r.getPlaceDate()));
        d.setOfficerName(trim(r.getOfficerName()));
        d.setOfficerTitle(trim(r.getOfficerTitle()));
        d.setUnitName(trim(r.getUnitName()));
        d.setUnitAddress(trim(r.getUnitAddress()));
        d.setUnitPhone(trim(r.getUnitPhone()));
        d.setReporterName(trim(r.getReporterName()));
        d.setReporterPhone(trim(r.getReporterPhone()));
        d.setTargetType(trim(r.getTargetType()));
        d.setIncidentTime(trim(r.getIncidentTime()));
        d.setIncidentAddress(trim(r.getIncidentAddress()));
        d.setCoordinates(trim(r.getCoordinates()));
        d.setDescription(trim(r.getDescription()));
        d.setAffectsCitizens(r.getAffectsCitizens());
        d.setNeedsHelp(r.getNeedsHelp());
        d.setHelpRequest(trim(r.getHelpRequest()));
        d.setAdviceGiven(trim(r.getAdviceGiven()));
        d.setResolutionResult(trim(r.getResolutionResult()));
    }

    private static void setCitizenSignature(IncidentDocument doc, SignDocumentRequest request, LocalDateTime now) {
        doc.setCitizenSignName(request.getName().trim());
        doc.setCitizenSignType(request.getType());
        doc.setCitizenSignature(request.getType() == SignatureType.DRAW ? request.getSignatureData() : null);
        doc.setCitizenSignedAt(now);
    }

    private static void validateSignature(SignDocumentRequest request) {
        if (request.getName() == null || request.getName().isBlank()) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng nhập họ và tên người ký");
        }
        if (request.getType() == SignatureType.DRAW
                && (request.getSignatureData() == null || request.getSignatureData().isBlank())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chưa có nét chữ ký vẽ tay");
        }
    }

    /** Ca cán bộ được làm văn bản: đã hoàn tất + cùng phạm vi xem (hoặc đơn vị xã được uỷ quyền đi xử lý) */
    private Incident officerIncident(OfficerPrincipal me, String incidentId) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy ca " + incidentId));
        boolean allowed = IncidentAccess.canView(me, incident) || incidentDispatchRepository
                .findByIncident_IdOrderByDispatchedAtAsc(incidentId).stream()
                .anyMatch(d -> IncidentAccess.isStationUnit(me, d));
        if (!allowed) throw new SosException(HttpStatus.FORBIDDEN, "Ca này ngoài phạm vi của bạn");
        if (incident.getStatus() != IncidentStatus.RESOLVED) {
            throw new SosException(HttpStatus.CONFLICT, "Chỉ lập văn bản báo cáo cho ca đã hoàn tất");
        }
        return incident;
    }

    private IncidentDocument savedOpenDocument(String incidentId) {
        IncidentDocument doc = documentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.CONFLICT, "Lưu văn bản trước khi ký"));
        if (doc.locked()) throw new SosException(HttpStatus.CONFLICT, "Văn bản đã đủ chữ ký 2 bên");
        return doc;
    }

    private IncidentDocument citizenDocument(String incidentId) {
        return documentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Cán bộ chưa lập văn bản báo cáo cho ca này"));
    }

    private Incident citizenIncident(String incidentId, String token) {
        Incident incident = incidentRepository.findById(incidentId)
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy ca " + incidentId));
        if (!CitizenTokens.matches(incident, token)) {
            throw new SosException(HttpStatus.FORBIDDEN, "Mã truy cập không hợp lệ");
        }
        return incident;
    }

    private Incident lookupIncident(String lookupCode, String phoneLast4) {
        SosException notFound = new SosException(HttpStatus.NOT_FOUND, "Mã tra cứu hoặc số điện thoại không đúng");
        String code = LookupCodes.normalize(lookupCode);
        if (code == null || phoneLast4 == null) throw notFound;
        Incident incident = incidentRepository.findByLookupCode(code).orElseThrow(() -> notFound);
        if (incident.getPhone() == null || !incident.getPhone().endsWith(phoneLast4)) throw notFound;
        return incident;
    }

    private static String gmapsUrl(Incident incident) {
        if (incident.getLatitude() == null || incident.getLongitude() == null) return null;
        return String.format(Locale.ROOT, "https://www.google.com/maps/search/?api=1&query=%.6f,%.6f",
                incident.getLatitude(), incident.getLongitude());
    }

    private OfficerPrincipal currentOfficer() {
        return currentOfficer.require();
    }

    private static String joinNonBlank(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) if (p != null && !p.isBlank()) kept.add(p.trim());
        return kept.isEmpty() ? null : String.join(sep, kept);
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** Nội dung mới (sau khi cắt khoảng trắng) khác nội dung đang lưu */
    private static boolean changes(IncidentDocument doc, IncidentDocumentRequest request) {
        IncidentDocument probe = new IncidentDocument();
        apply(probe, request);
        return !Objects.equals(IncidentDocumentResponse.fieldsOf(doc), IncidentDocumentResponse.fieldsOf(probe));
    }
}
