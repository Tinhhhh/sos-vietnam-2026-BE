package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.service.AccountService;
import com.sosvietnam.util.AccountExcelExporter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class AccountServiceImpl implements AccountService {

    private static final Map<String, Integer> ROLE_ORDER = Map.of("ADMIN", 0, "OFFICER", 1, "CITIZEN", 2);
    private static final Set<String> STAFF_ROLES = Set.of("ADMIN", "OFFICER");

    private final AccountRepository accountRepository;
    private final AccountExcelExporter accountExcelExporter;
    private final PasswordEncoder passwordEncoder;

    // Mật khẩu seed trong .env: chỉ những mật khẩu này mới ghi được ra file (DB chỉ có bcrypt)
    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.test-account-password:}")
    private String testAccountPassword;

    @Override
    public byte[] exportAccountsToExcel() throws IOException {
        Comparator<Account> order = Comparator
                .comparing((Account a) -> ROLE_ORDER.getOrDefault(a.getRole() != null ? a.getRole().getRoleName() : "", 9))
                .thenComparing(Account::getProvince, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getAgencyType, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getUnitLevel, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Account::getEmail);
        List<Account> accounts = accountRepository.findAll().stream().sorted(order).toList();
        List<String> seedPasswords = Stream.of(adminPassword, testAccountPassword).filter(StringUtils::hasText).toList();
        return accountExcelExporter.export(accounts, account -> seedPasswordOf(account, seedPasswords));
    }

    /** Mật khẩu gốc nếu tài khoản trực ban còn dùng mật khẩu seed, null nếu đã đổi (hoặc là người dân). */
    private String seedPasswordOf(Account account, List<String> seedPasswords) {
        if (account.getRole() == null || !STAFF_ROLES.contains(account.getRole().getRoleName())) return null;
        return seedPasswords.stream()
                .filter(plain -> passwordEncoder.matches(plain, account.getPassword()))
                .findFirst()
                .orElse(null);
    }
}
