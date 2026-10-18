package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
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

    // Mật khẩu seed trong .env: chỉ những mật khẩu này mới ghi được ra file (DB chỉ có bcrypt)
    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.test-account-password:}")
    private String testAccountPassword;

    @Override
    public byte[] exportAccountsToExcel() throws IOException {
        Comparator<Account> order = Comparator
                .comparing((Account a) -> roleOf(a).map(Enum::ordinal).orElse(Integer.MAX_VALUE))
                .thenComparing(Account::provinceName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getAgencyType, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getUnitLevel, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getEmail);
        List<Account> accounts = accountRepository.findAll().stream().sorted(order).toList();
        List<String> seedPasswords = Stream.of(adminPassword, testAccountPassword).filter(StringUtils::hasText).toList();
        return accountExcelExporter.export(accounts, account -> seedPasswordOf(account, seedPasswords));
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
                .build();

        Account saved = accountRepository.save(account);
        log.info("[ADMIN CREATE ACCOUNT] {} ({}, {} {})", saved.getEmail(), role.getRoleName(), agency, level);
        return AccountInfoResponse.from(saved);
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
