package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.UnitDutyAssignment;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.UnitRole;
import com.sosvietnam.util.LookupCodes;
import java.time.LocalDateTime;
import java.util.UUID;
import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.NewAccountRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.AccountService;
import com.sosvietnam.util.AccountExcelExporter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private final AccountRepository accountRepository;
    private final AccountExcelExporter accountExcelExporter;
    private final PasswordEncoder passwordEncoder;
    private final RoleRepository roleRepository;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final com.sosvietnam.repository.UnitMemberRepository unitMemberRepository;
    private final com.sosvietnam.repository.UnitDutyAssignmentRepository dutyRepository;

    // Mật khẩu seed trong .env: chỉ những mật khẩu này mới ghi được ra file (DB chỉ có bcrypt)
    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.test-account-password:}")
    private String testAccountPassword;

    @Value("${app.seed.member-pin:}")
    private String memberPin;

    // 1 phiên Hibernate cho cả lần xuất: tài khoản (đã fetch role/tỉnh/xã) được dùng lại khi nạp cán bộ, không N+1
    @Transactional(readOnly = true)
    @Override
    public byte[] exportAccountsToExcel(String provinceId, AgencyType agency, UnitLevel level) throws IOException {
        if (StringUtils.hasText(provinceId) && !provinceRepository.existsById(provinceId)) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Mã tỉnh/thành phố không hợp lệ");
        }
        String province = StringUtils.hasText(provinceId) ? provinceId : null;
        Comparator<Account> order = Comparator
                .comparing((Account a) -> roleOf(a).map(Enum::ordinal).orElse(Integer.MAX_VALUE))
                .thenComparing(Account::provinceName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getAgencyType, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getUnitLevel, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getEmail);
        List<Account> accounts = accountRepository.findForExport(province, agency, level).stream().sorted(order).toList();

        // cán bộ theo đúng thứ tự đơn vị ở sheet tài khoản, trong đơn vị: chỉ huy trước, rồi theo tên
        java.util.Map<UUID, Integer> position = new java.util.HashMap<>();
        for (int i = 0; i < accounts.size(); i++) position.put(accounts.get(i).getId(), i);
        List<UnitMember> members = unitMemberRepository.findForExport(province, agency, level).stream()
                .filter(m -> position.containsKey(m.getAccount().getId()))
                .sorted(Comparator.comparing((UnitMember m) -> position.get(m.getAccount().getId()))
                        .thenComparing(UnitMember::getUnitRole)
                        .thenComparing(UnitMember::getFullName))
                .toList();
        java.util.Map<UUID, UUID> dutyByAccount = new java.util.HashMap<>();
        for (Object[] row : dutyRepository.findOpenDutyIds()) dutyByAccount.put((UUID) row[0], (UUID) row[1]);

        List<String> seedPasswords = Stream.of(adminPassword, testAccountPassword).filter(StringUtils::hasText).toList();
        // hàng nghìn tài khoản đơn vị seed dùng chung 1 bản băm: so bcrypt 1 lần cho mỗi bản băm khác nhau
        java.util.Map<String, Optional<String>> byHash = new java.util.HashMap<>();
        java.util.Map<String, Optional<String>> pinByHash = new java.util.HashMap<>();
        return accountExcelExporter.export(accounts,
                account -> byHash
                        .computeIfAbsent(account.getPassword() + "|" + roleOf(account).map(Enum::name).orElse(""),
                                k -> Optional.ofNullable(seedPasswordOf(account, seedPasswords)))
                        .orElse(null),
                members, dutyByAccount,
                member -> member.getPinHash() == null || !StringUtils.hasText(memberPin) ? null : pinByHash
                        .computeIfAbsent(member.getPinHash(),
                                hash -> Optional.of(memberPin).filter(pin -> passwordEncoder.matches(pin, hash)))
                        .orElse(null));
    }

    @Transactional
    @Override
    public AccountInfoResponse createAccountByAdmin(NewAccountRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        if (accountRepository.existsByEmail(email)) {
            throw new SosException(HttpStatus.CONFLICT, "Email đã tồn tại: " + email);
        }
        if (request.getRoleName() != RoleName.OFFICER && request.getRoleName() != RoleName.ADMIN) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Chỉ tạo được tài khoản cán bộ hoặc quản trị");
        }

        AgencyType agency = request.getAgencyType();
        UnitLevel level = request.getUnitLevel();
        if (request.getRoleName() == RoleName.OFFICER) {
            if (agency == null) {
                throw new SosException(HttpStatus.BAD_REQUEST, "Vui lòng chọn lực lượng");
            }
            if (level == null || !agency.supports(level)) {
                throw new SosException(HttpStatus.BAD_REQUEST,
                        agency.getLabel() + " không có " + (level == null ? "cấp đã chọn" : agency.levelLabel(level)));
            }
        } else {
            // Quản trị = Trung tâm chỉ huy, không thuộc lực lượng nào
            agency = null;
            level = UnitLevel.NATIONAL;
        }

        Role role = roleRepository.findByRoleName(request.getRoleName().getRole())
                .orElseThrow(() -> new SosException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role " + request.getRoleName().getRole() + " chưa được khởi tạo"));
        Province province = provinceRepository.findById(request.getProvinceId())
                .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Mã tỉnh/thành phố không hợp lệ"));
        Ward ward = wardRepository.findById(request.getWardId())
                .filter(w -> w.getProvince() != null && w.getProvince().getCode().equals(province.getCode()))
                .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Xã/phường không thuộc tỉnh đã chọn"));

        Account account = Account.builder()
                .lastName(request.getLastName().trim())
                .firstName(request.getFirstName().trim())
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(StringUtils.hasText(request.getAddress()) ? request.getAddress().trim() : null)
                .role(role)
                .agencyType(agency)
                .unitLevel(level)
                .province(province)
                .ward(ward)
                .isActive(true)
                .isLocked(false)
                .unitAccount(request.getRoleName() == RoleName.OFFICER)
                .build();

        Account saved = accountRepository.save(account);
        AccountInfoResponse response = AccountInfoResponse.from(saved);
        LocalDateTime now = LocalDateTime.now();
        if (request.getRoleName() == RoleName.OFFICER) {
            // Tài khoản đơn vị: người được tạo là chỉ huy đầu tiên (kiêm trực ban), tự đặt PIN bằng mã kích hoạt
            String code = LookupCodes.generate();
            UnitMember commander = unitMemberRepository.save(UnitMember.builder()
                    .id(UUID.randomUUID())
                    .account(saved)
                    .fullName(saved.fullName())
                    .unitRole(UnitRole.COMMANDER)
                    .phone(saved.getPhone())
                    .activationCodeHash(passwordEncoder.encode(code))
                    .activationExpiresAt(now.plusHours(72))
                    .createdAt(now)
                    .build());
            dutyRepository.save(UnitDutyAssignment.builder().account(saved).member(commander).startedAt(now).build());
            response.setActivationCode(code);
        } else {
            // Quản trị: cán bộ ẩn cùng id, đăng nhập không qua PIN
            unitMemberRepository.save(UnitMember.builder()
                    .id(saved.getId()).account(saved).fullName(saved.fullName()).unitRole(UnitRole.COMMANDER)
                    .createdAt(now).build());
        }
        log.info("[ADMIN CREATE ACCOUNT] {} ({}, {} {})", saved.getEmail(), role.getRoleName(), agency, level);
        return response;
    }

    private static Optional<RoleName> roleOf(Account account) {
        return account.getRole() == null ? Optional.empty() : RoleName.fromRole(account.getRole().getRoleName());
    }

    /** Mật khẩu gốc nếu tài khoản trực ban còn dùng mật khẩu seed, null nếu đã đổi (hoặc là người dân). */
    private String seedPasswordOf(Account account, List<String> seedPasswords) {
        if (!roleOf(account).map(RoleName::isStaff).orElse(false)) return null;
        return seedPasswords.stream()
                .filter(plain -> passwordEncoder.matches(plain, account.getPassword()))
                .findFirst()
                .orElse(null);
    }
}
