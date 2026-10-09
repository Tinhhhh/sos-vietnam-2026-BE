package com.sosvietnam.config;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;

    // From .env (SEED_ADMIN_PASSWORD / SEED_DISPATCHER_PASSWORD). Empty = don't create that account,
    // so a fresh server never comes up with a password that is written in this public repo.
    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    @Value("${app.seed.dispatcher-password:}")
    private String dispatcherPassword;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedDefaultAccounts();
    }

    private void seedRoles() {
        List<String> defaultRoles = List.of("ADMIN", "DISPATCHER", "OFFICER", "CITIZEN");
        for (String rName : defaultRoles) {
            if (!roleRepository.existsByRoleName(rName)) {
                roleRepository.save(Role.builder()
                        .roleName(rName)
                        .description("Quyền hệ thống: " + rName)
                        .build());
                log.info("🛡️ [INIT ROLE] Seeded role: {}", rName);
            }
        }
    }

    private void seedDefaultAccounts() {
        if (!accountRepository.existsByEmail("admin@sos.vn") && hasSeedPassword("admin@sos.vn", adminPassword, "SEED_ADMIN_PASSWORD")) {
            Role adminRole = roleRepository.findByRoleName("ADMIN").orElse(null);
            Account admin = Account.builder()
                    .firstName("Quản Trị")
                    .lastName("Hệ Thống")
                    .email("admin@sos.vn")
                    .password(passwordEncoder.encode(adminPassword))
                    .phone("0988888888")
                    .address("Bộ Chỉ Huy SOS Việt Nam")
                    .role(adminRole)
                    .agencyType(AgencyType.MILITARY)
                    .province("TP. Hà Nội")
                    .ward("Phường Hàng Trống")
                    .isActive(true)
                    .build();
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Seeded Admin Account: admin@sos.vn");
        }

        if (!accountRepository.existsByEmail("dispatcher@sos.vn") && hasSeedPassword("dispatcher@sos.vn", dispatcherPassword, "SEED_DISPATCHER_PASSWORD")) {
            Role dispRole = roleRepository.findByRoleName("DISPATCHER").orElse(null);
            Account dispatcher = Account.builder()
                    .firstName("Trực Ban")
                    .lastName("Điều Phối")
                    .email("dispatcher@sos.vn")
                    .password(passwordEncoder.encode(dispatcherPassword))
                    .phone("0977777777")
                    .address("Trung Tâm Điều Phối Cứu Nạn")
                    .role(dispRole)
                    .agencyType(AgencyType.POLICE)
                    .province("TP. Hà Nội")
                    .ward("Phường Tràng Tiền")
                    .isActive(true)
                    .build();
            accountRepository.save(dispatcher);
            log.info("🚒 [INIT ACCOUNT] Seeded Dispatcher Account: dispatcher@sos.vn");
        }
    }

    private static boolean hasSeedPassword(String email, String password, String envName) {
        if (StringUtils.hasText(password)) {
            return true;
        }
        log.warn("⚠️ [INIT ACCOUNT] {} not created: set {} in .env to seed it", email, envName);
        return false;
    }
}