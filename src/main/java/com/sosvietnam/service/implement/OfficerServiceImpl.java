package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.OfficerService;
import com.sosvietnam.util.IncidentAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private final AccountRepository accountRepository;
    private final IncidentRepository incidentRepository;
    private final IncidentNotifier notifier;

    @Override
    @Transactional(readOnly = true)
    public List<OfficerStatusResponse> getUnitOfficers() {
        OfficerPrincipal me = currentOfficer();
        List<Account> officers = me.isAdmin()
                ? accountRepository.findByRole_RoleNameAndIsActiveTrue(RoleName.OFFICER.getRole())
                : accountRepository.findActiveOfficers(me.agency(), me.level()).stream()
                        .filter(a -> IncidentAccess.sameUnit(me, OfficerPrincipal.of(a)))
                        .toList();
        return describe(officers, me);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OfficerStatusResponse> describe(List<Account> officers) {
        return describe(officers, currentOfficer());
    }

    private List<OfficerStatusResponse> describe(List<Account> officers, OfficerPrincipal me) {
        Map<UUID, Incident> held = officers.isEmpty() ? Map.of()
                : incidentRepository.findByHandlerIdInAndStatusIn(
                                officers.stream().map(Account::getId).toList(), IncidentStatus.ACTIVE).stream()
                        .collect(Collectors.toMap(Incident::getHandlerId, Function.identity(), (a, b) -> a));
        Set<String> online = notifier.onlineEmails();

        return officers.stream()
                .map(a -> toResponse(a, me, online.contains(a.getEmail()), held.get(a.getId())))
                .sorted(Comparator.comparingInt(OfficerServiceImpl::rank)
                        .thenComparing(OfficerStatusResponse::getFullName, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    private static OfficerStatusResponse toResponse(Account a, OfficerPrincipal me, boolean online, Incident held) {
        String unit = a.getAgencyType() == null ? null
                : a.getAgencyType().getLabel()
                + (a.getUnitLevel() == null ? "" : " · " + a.getAgencyType().levelLabel(a.getUnitLevel()));
        return OfficerStatusResponse.builder()
                .accountId(a.getId())
                .fullName(a.fullName())
                .email(a.getEmail())
                .phone(a.getPhone())
                .agencyType(a.getAgencyType())
                .unitLevel(a.getUnitLevel())
                .unitLabel(unit)
                .province(a.provinceName())
                .ward(a.wardName())
                .online(online)
                .busy(held != null)
                .me(a.getId().equals(me.accountId()))
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        OfficerPrincipal officer = OfficerPrincipal.of(account);
        if (!officer.isStaff()) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ mới xem được trạng thái cán bộ");
        }
        return officer;
    }
}
