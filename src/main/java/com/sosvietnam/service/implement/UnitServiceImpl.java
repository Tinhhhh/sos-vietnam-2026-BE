package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.UnitDutyAssignment;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.UnitMemberRequest;
import com.sosvietnam.model.payload.response.ActivationCodeResponse;
import com.sosvietnam.model.payload.response.DutyResponse;
import com.sosvietnam.model.payload.response.UnitMemberResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.UnitDutyAssignmentRepository;
import com.sosvietnam.repository.UnitMemberRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.security.JwtTokenProvider;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.security.StompSessionRegistry;
import com.sosvietnam.service.UnitService;
import com.sosvietnam.util.LookupCodes;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UnitServiceImpl implements UnitService {

    /** Mã kích hoạt còn hạn trong 72 giờ. */
    static final long ACTIVATION_HOURS = 72;
    private static final int HISTORY_SIZE = 50;

    private final UnitMemberRepository unitMemberRepository;
    private final UnitDutyAssignmentRepository dutyRepository;
    private final AccountRepository accountRepository;
    private final IncidentRepository incidentRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final StompSessionRegistry stompSessionRegistry;
    private final CurrentOfficer currentOfficer;

    @Override
    @Transactional(readOnly = true)
    public List<UnitMemberResponse> getMembers() {
        OfficerPrincipal me = currentOfficer.require();
        UUID duty = currentDutyMemberId(me.unitAccountId());
        return unitMemberRepository.findByAccount_IdAndActiveTrueOrderByFullNameAsc(me.unitAccountId()).stream()
                .map(m -> UnitMemberResponse.from(m, m.getId().equals(duty)))
                .toList();
    }

    @Override
    @Transactional
    public ActivationCodeResponse createMember(UnitMemberRequest request) {
        OfficerPrincipal me = requireManager();
        String badge = trimToNull(request.getBadgeNumber());
        checkBadge(me.unitAccountId(), badge, null);
        UnitMember member = UnitMember.builder()
                .id(UUID.randomUUID())
                .account(accountRepository.getReferenceById(me.unitAccountId()))
                .createdBy(me.memberId())
                .createdAt(LocalDateTime.now())
                .build();
        apply(member, request, badge);
        String code = newActivation(member);
        unitMemberRepository.save(member);
        log.info("[UNIT] {} thêm cán bộ {} vào {}", me.fullName(), member.getFullName(), me.email());
        return ActivationCodeResponse.builder()
                .member(UnitMemberResponse.from(member, false))
                .activationCode(code)
                .activationExpiresAt(member.getActivationExpiresAt())
                .build();
    }

    @Override
    @Transactional
    public UnitMemberResponse updateMember(UUID memberId, UnitMemberRequest request) {
        OfficerPrincipal me = requireManager();
        UnitMember member = memberOfMyUnit(me, memberId);
        String badge = trimToNull(request.getBadgeNumber());
        checkBadge(me.unitAccountId(), badge, member.getId());
        apply(member, request, badge);
        unitMemberRepository.save(member);
        return UnitMemberResponse.from(member, member.getId().equals(currentDutyMemberId(me.unitAccountId())));
    }

    @Override
    @Transactional
    public void deleteMember(UUID memberId) {
        OfficerPrincipal me = requireManager();
        UnitMember member = memberOfMyUnit(me, memberId);
        if (me.is(member.getId())) {
            throw new SosException(HttpStatus.CONFLICT, "Không tự xoá chính mình khỏi đơn vị");
        }
        if (member.getId().equals(currentDutyMemberId(me.unitAccountId()))) {
            throw new SosException(HttpStatus.CONFLICT, "Cán bộ đang trực ban, giao trực ban cho người khác trước");
        }
        if (incidentRepository.existsByHandlerIdAndStatusIn(member.getId(), IncidentStatus.ACTIVE)) {
            throw new SosException(HttpStatus.CONFLICT, member.getFullName() + " đang giữ một ca, chờ giao hoặc kết thúc ca trước");
        }
        member.setActive(false);
        unitMemberRepository.save(member);
        endSessions(member);
        log.info("[UNIT] {} xoá cán bộ {} khỏi {}", me.fullName(), member.getFullName(), me.email());
    }

    @Override
    @Transactional
    public ActivationCodeResponse resetPin(UUID memberId) {
        OfficerPrincipal me = requireManager();
        UnitMember member = memberOfMyUnit(me, memberId);
        member.setPinHash(null);
        member.setFailedPinCount(0);
        member.setLockedAt(null);
        String code = newActivation(member);
        unitMemberRepository.save(member);
        endSessions(member);
        log.info("[UNIT] {} cấp lại PIN cho {}", me.fullName(), member.getFullName());
        return ActivationCodeResponse.builder()
                .activationCode(code)
                .activationExpiresAt(member.getActivationExpiresAt())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public DutyResponse getDuty() {
        OfficerPrincipal me = currentOfficer.require();
        return DutyResponse.builder()
                .current(dutyRepository.findFirstByAccount_IdAndEndedAtIsNull(me.unitAccountId())
                        .map(DutyResponse.Assignment::from).orElse(null))
                .history(dutyRepository.findByAccount_IdOrderByStartedAtDesc(me.unitAccountId(), PageRequest.of(0, HISTORY_SIZE))
                        .stream().map(DutyResponse.Assignment::from).toList())
                .build();
    }

    @Override
    @Transactional
    public DutyResponse.Assignment assignDuty(UUID memberId) {
        OfficerPrincipal me = requireManager();
        UnitMember target = memberOfMyUnit(me, memberId);
        if (target.isLocked()) {
            throw new SosException(HttpStatus.CONFLICT, "Hồ sơ của " + target.getFullName() + " đang bị khoá");
        }
        LocalDateTime now = LocalDateTime.now();
        UnitDutyAssignment current = dutyRepository.findFirstByAccount_IdAndEndedAtIsNull(me.unitAccountId()).orElse(null);
        if (current != null) {
            if (current.getMember().getId().equals(target.getId())) {
                throw new SosException(HttpStatus.CONFLICT, target.getFullName() + " đang là trực ban");
            }
            current.setEndedAt(now);
            dutyRepository.saveAndFlush(current);
        }
        UnitDutyAssignment next = UnitDutyAssignment.builder()
                .account(accountRepository.getReferenceById(me.unitAccountId()))
                .member(target)
                .assignedBy(unitMemberRepository.getReferenceById(me.memberId()))
                .startedAt(now)
                .build();
        try {
            dutyRepository.saveAndFlush(next);
        } catch (DataIntegrityViolationException e) {
            // ux_unit_duty_current: hai người cùng bấm giao trực ban
            throw new SosException(HttpStatus.CONFLICT, "Trực ban vừa được giao bởi người khác, tải lại để xem");
        }
        log.info("[DUTY] {}: {} giao trực ban cho {}", me.email(), me.fullName(), target.getFullName());
        return DutyResponse.Assignment.from(next);
    }

    /** Chỉ huy, hoặc trực ban hiện tại của đơn vị. */
    private OfficerPrincipal requireManager() {
        OfficerPrincipal me = currentOfficer.require();
        if (!me.isCommander() && !me.is(currentDutyMemberId(me.unitAccountId()))) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ chỉ huy hoặc trực ban mới được thay đổi");
        }
        return me;
    }

    private UUID currentDutyMemberId(UUID accountId) {
        return dutyRepository.findFirstByAccount_IdAndEndedAtIsNull(accountId)
                .map(d -> d.getMember().getId()).orElse(null);
    }

    private UnitMember memberOfMyUnit(OfficerPrincipal me, UUID memberId) {
        return unitMemberRepository.findById(memberId)
                .filter(m -> m.isActive() && m.getAccount().getId().equals(me.unitAccountId()))
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy cán bộ trong đơn vị"));
    }

    private void checkBadge(UUID accountId, String badge, UUID self) {
        if (badge != null && unitMemberRepository.existsByAccount_IdAndBadgeNumberAndActiveTrueAndIdNot(
                accountId, badge, self == null ? new UUID(0, 0) : self)) {
            throw new SosException(HttpStatus.CONFLICT, "Số hiệu " + badge + " đã có trong đơn vị");
        }
    }

    private static void apply(UnitMember member, UnitMemberRequest request, String badge) {
        member.setFullName(request.getFullName().trim());
        member.setBadgeNumber(badge);
        member.setRank(request.getRank());
        member.setPosition(trimToNull(request.getPosition()));
        member.setUnitRole(request.getUnitRole());
        member.setPhone(trimToNull(request.getPhone()));
    }

    /** Sinh mã kích hoạt mới (chỉ lưu bản băm), trả mã gốc để hiện 1 lần. */
    private String newActivation(UnitMember member) {
        String code = LookupCodes.generate();
        member.setActivationCodeHash(passwordEncoder.encode(code));
        member.setActivationExpiresAt(LocalDateTime.now().plusHours(ACTIVATION_HOURS));
        return code;
    }

    private void endSessions(UnitMember member) {
        jwtTokenProvider.disableOldTokens(member.getId());
        stompSessionRegistry.closeAll(member.getId().toString());
    }

    private static String trimToNull(String s) {
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
