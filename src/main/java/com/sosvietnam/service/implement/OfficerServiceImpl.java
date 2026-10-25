package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.OfficerService;
import com.sosvietnam.util.UnitMemberSpecification;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class OfficerServiceImpl implements OfficerService {

    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final com.sosvietnam.security.CurrentOfficer currentOfficer;
    private final IncidentRepository incidentRepository;
    private final IncidentNotifier notifier;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;

    private static final int MAX_PAGE_SIZE = 100;

    @Override
    @Transactional(readOnly = true)
    public ListResponse getUnitOfficers(UnitLevel level, String provinceId, Long wardId, AgencyType agency,
                                        Boolean online, int pageNo, int pageSize) {
        OfficerPrincipal me = currentOfficer();
        if (pageNo < 0 || pageSize < 1) {
            throw new SosException(HttpStatus.BAD_REQUEST, "pageNo >= 0, pageSize >= 1");
        }
        Specification<UnitMember> spec = Specification.where(UnitMemberSpecification.activeOfficers());
        if (me.isAdmin()) {
            spec = spec.and(adminUnitFilter(level, provinceId, wardId, agency));
        } else {
            // đơn vị của người gọi (IncidentAccess.sameUnit); chưa xác định địa bàn thì không thấy ai khác
            if (me.agency() == null || me.provinceId() == null || (me.level() == UnitLevel.WARD && me.wardId() == null)) {
                spec = spec.and(UnitMemberSpecification.idIn(List.of(me.memberId())));
            } else if (me.level() == UnitLevel.PROVINCE && level == UnitLevel.WARD) {
                // cấp tỉnh xem đơn vị xã cùng lực lượng của một xã trong tỉnh mình
                spec = spec.and(UnitMemberSpecification.hasAgency(me.agency()))
                        .and(UnitMemberSpecification.hasLevel(UnitLevel.WARD))
                        .and(UnitMemberSpecification.inProvince(me.provinceId()))
                        .and(UnitMemberSpecification.inWard(requireWardOf(me.provinceId(), wardId)));
            } else {
                spec = spec.and(UnitMemberSpecification.hasAgency(me.agency()))
                        .and(UnitMemberSpecification.hasLevel(me.level()))
                        .and(UnitMemberSpecification.inProvince(me.provinceId()))
                        .and(me.level() == UnitLevel.WARD ? UnitMemberSpecification.inWard(me.wardId()) : null);
            }
        }
        if (Boolean.TRUE.equals(online)) {
            spec = spec.and(UnitMemberSpecification.idIn(notifier.onlineMemberIds()));
        }

        // đơn vị (email tài khoản) -> chỉ huy trước -> tên; trong trang xếp lại theo trực tuyến / giữ ca
        Pageable pageable = PageRequest.of(pageNo, Math.min(pageSize, MAX_PAGE_SIZE),
                Sort.by("account.email", "unitRole", "fullName"));
        Page<UnitMember> page = unitMemberRepository.findAll(spec, pageable);

        ListResponse listResponse = new ListResponse();
        listResponse.setContent(describe(page.getContent(), me));
        listResponse.setPageNo(page.getNumber());
        listResponse.setPageSize(page.getSize());
        listResponse.setTotalElements(page.getTotalElements());
        listResponse.setTotalPages(page.getTotalPages());
        listResponse.setLast(page.isLast());
        return listResponse;
    }

    /** Xã đã chọn phải có và thuộc tỉnh; trả lại wardId. */
    private Long requireWardOf(String provinceId, Long wardId) {
        if (wardId == null) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Cấp xã: chọn xã/phường (ward_id)");
        }
        boolean inProvince = wardRepository.findById(wardId)
                .map(w -> w.getProvince() != null && provinceId.equals(w.getProvince().getCode()))
                .orElse(false);
        if (!inProvince) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Xã/phường không thuộc tỉnh đã chọn");
        }
        return wardId;
    }

    /** ADMIN chọn đúng 1 đơn vị: cấp + tỉnh (+ xã với cấp xã), lực lượng tuỳ chọn. */
    private Specification<UnitMember> adminUnitFilter(UnitLevel level, String provinceId, Long wardId,
                                                      AgencyType agency) {
        if (level == null || level == UnitLevel.NATIONAL) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chọn cấp đơn vị: PROVINCE (tỉnh) hoặc WARD (xã)");
        }
        if (!StringUtils.hasText(provinceId) || !provinceRepository.existsById(provinceId)) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chọn tỉnh/thành phố hợp lệ (province_id)");
        }
        if (level == UnitLevel.WARD) {
            requireWardOf(provinceId, wardId);
        }
        return Specification.where(UnitMemberSpecification.hasLevel(level))
                .and(UnitMemberSpecification.inProvince(provinceId))
                .and(level == UnitLevel.WARD ? UnitMemberSpecification.inWard(wardId) : null)
                .and(UnitMemberSpecification.hasAgency(agency));
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficerStatusResponse> describe(List<UnitMember> officers) {
        return describe(officers, currentOfficer());
    }

    private List<OfficerStatusResponse> describe(List<UnitMember> officers, OfficerPrincipal me) {
        Map<UUID, Incident> held = officers.isEmpty() ? Map.of()
                : incidentRepository.findByHandlerIdInAndStatusIn(
                                officers.stream().map(UnitMember::getId).toList(), IncidentStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Incident::getHandlerId, Function.identity(), (a, b) -> a));
        Set<UUID> online = notifier.onlineMemberIds();

        return officers.stream()
                .map(m -> toResponse(m, me, online.contains(m.getId()), held.get(m.getId())))
                .sorted(Comparator.comparingInt(OfficerServiceImpl::rank)
                        .thenComparing(OfficerStatusResponse::getFullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static OfficerStatusResponse toResponse(UnitMember m, OfficerPrincipal me, boolean online, Incident held) {
        Account a = m.getAccount();
        String unit = a.getAgencyType() == null ? null
                : a.getAgencyType().getLabel()
                + (a.getUnitLevel() == null ? "" : " · " + a.getAgencyType().levelLabel(a.getUnitLevel()));
        return OfficerStatusResponse.builder()
                .memberId(m.getId())
                .fullName(m.displayName())
                .email(a.getEmail())
                .phone(m.getPhone() != null ? m.getPhone() : a.getPhone())
                .agencyType(a.getAgencyType())
                .unitLevel(a.getUnitLevel())
                .unitLabel(unit)
                .province(a.provinceName())
                .ward(a.wardName())
                .online(online)
                .busy(held != null)
                .me(me.is(m.getId()))
                .heldIncident(held == null ? null : OfficerStatusResponse.HeldIncident.builder()
                        .id(held.getId())
                        .status(held.getStatus())
                        .statusLabel(held.getStatus() == null ? null : held.getStatus().getLabel())
                        .emergencyType(held.getEmergencyType())
                        .build())
                .build();
    }

    /** Trực tuyến & rảnh (0) -> trực tuyến & đang giữ ca (1) -> ngoại tuyến (2). */
    private static int rank(OfficerStatusResponse o) {
        if (!o.isOnline()) return 2;
        return o.isBusy() ? 1 : 0;
    }

    private OfficerPrincipal currentOfficer() {
        return currentOfficer.require();
    }
}
