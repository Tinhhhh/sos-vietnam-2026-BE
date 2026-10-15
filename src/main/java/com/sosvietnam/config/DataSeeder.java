package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final RescueStationRepository rescueStationRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String TEST_ACCOUNTS_FILE = "data/test_accounts.json";
    private static final String STATIONS_FILE = "data/rescue_stations.json";
    private static final String STATIONS_SEED_NAME = "rescue_stations";
    private static final String UPSERT_STATION_SQL =
            "INSERT INTO rescue_stations (code, name, agency_type, phone, address, province, ward, latitude, longitude, location) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326)) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, agency_type = EXCLUDED.agency_type, "
            + "phone = EXCLUDED.phone, address = EXCLUDED.address, province = EXCLUDED.province, ward = EXCLUDED.ward, "
            + "latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude, location = EXCLUDED.location";

    // From .env (SEED_ADMIN_PASSWORD). Empty = don't seed the admin account,
    // so a fresh server never comes up with a password written in this public repo.
    @Value("${app.seed.admin-password:}")
    private String adminPassword;

    // From .env (SEED_TEST_ACCOUNT_PASSWORD), shared by every account in data/test_accounts.json.
    // Empty = the test accounts are not imported.
    @Value("${app.seed.test-account-password:}")
    private String testAccountPassword;

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedDefaultAccounts();
        seedTestAccounts();
        seedRescueStations();
    }

    private void seedRoles() {
        List<String> defaultRoles = List.of("ADMIN", "OFFICER", "CITIZEN");
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
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("⚠️ [INIT ACCOUNT] admin@sos.vn not seeded: set SEED_ADMIN_PASSWORD in .env");
            return;
        }
        Account admin = accountRepository.findByEmail("admin@sos.vn").orElse(null);
        if (admin == null) {
            Role adminRole = roleRepository.findByRoleName("ADMIN").orElse(null);
            admin = Account.builder()
                    .firstName("Quản Trị")
                    .lastName("Hệ Thống")
                    .email("admin@sos.vn")
                    .password(passwordEncoder.encode(adminPassword))
                    .phone("0988888888")
                    .address("Bộ Chỉ Huy SOS Việt Nam")
                    .role(adminRole)
                    .agencyType(AgencyType.MILITARY)
                    .unitLevel(UnitLevel.NATIONAL)
                    .province("TP. Hà Nội")
                    .ward("Phường Hàng Trống")
                    .isActive(true)
                    .build();
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Seeded Admin Account: admin@sos.vn");
        } else {
            boolean changed = false;
            if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
                admin.setPassword(passwordEncoder.encode(adminPassword));
                changed = true;
                log.info("👑 [INIT ACCOUNT] Synchronized Admin Account password for: admin@sos.vn");
            }
            if (admin.getUnitLevel() != UnitLevel.NATIONAL) {
                admin.setUnitLevel(UnitLevel.NATIONAL);
                changed = true;
            }
            if (changed) {
                accountRepository.save(admin);
            }
        }
    }

    /**
     * Nạp tài khoản trực ban test từ data/test_accounts.json (theo email): chưa có thì tạo,
     * có rồi thì cập nhật lực lượng / cấp / địa bàn và đồng bộ mật khẩu với .env.
     */
    private void seedTestAccounts() {
        if (!StringUtils.hasText(testAccountPassword)) {
            log.info("[INIT TEST ACCOUNTS] Skipped: set SEED_TEST_ACCOUNT_PASSWORD in .env to import {}", TEST_ACCOUNTS_FILE);
            return;
        }
        try {
            ClassPathResource resource = new ClassPathResource(TEST_ACCOUNTS_FILE);
            if (!resource.exists()) {
                log.warn("⚠️ [INIT TEST ACCOUNTS] {} not found in classpath", TEST_ACCOUNTS_FILE);
                return;
            }
            JsonNode accountsNode;
            try (InputStream in = resource.getInputStream()) {
                accountsNode = objectMapper.readTree(in).path("accounts");
            }
            Role officerRole = roleRepository.findByRoleName("OFFICER")
                    .orElseThrow(() -> new IllegalStateException("Role OFFICER chưa được khởi tạo"));

            int created = 0;
            int updated = 0;
            for (JsonNode node : accountsNode) {
                String email = node.path("email").asText("").trim();
                AgencyType agency = AgencyType.valueOf(node.path("agency_type").asText());
                UnitLevel level = UnitLevel.valueOf(node.path("unit_level").asText());
                if (!StringUtils.hasText(email) || !agency.supports(level)) {
                    log.warn("⚠️ [INIT TEST ACCOUNTS] Skipped {}: {} has no level {}", email, agency, level);
                    continue;
                }

                Account account = accountRepository.findByEmail(email).orElse(null);
                boolean isNew = account == null;
                if (isNew) {
                    account = Account.builder().email(email).role(officerRole).isActive(true).build();
                }
                account.setLastName(textOrNull(node, "last_name"));
                account.setFirstName(textOrNull(node, "first_name"));
                account.setAgencyType(agency);
                account.setUnitLevel(level);
                account.setProvince(textOrNull(node, "province"));
                account.setWard(textOrNull(node, "ward"));
                account.setAddress(textOrNull(node, "address"));
                if (isNew || !passwordEncoder.matches(testAccountPassword, account.getPassword())) {
                    account.setPassword(passwordEncoder.encode(testAccountPassword));
                }
                accountRepository.save(account);
                if (isNew) created++;
                else updated++;
            }
            log.info("[INIT TEST ACCOUNTS] {} created, {} updated from {}", created, updated, TEST_ACCOUNTS_FILE);
        } catch (Exception e) {
            log.error("❌ [INIT TEST ACCOUNTS] Failed to import test accounts: {}", e.getMessage(), e);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() || value.asText().isBlank() ? null : value.asText().trim();
    }

    private void seedRescueStations() {
        try {
            ClassPathResource resource = new ClassPathResource(STATIONS_FILE);
            if (!resource.exists()) {
                log.warn("⚠️ [INIT STATIONS] {} not found in classpath", STATIONS_FILE);
                return;
            }
            byte[] content;
            try (InputStream in = resource.getInputStream()) {
                content = in.readAllBytes();
            }

            // 1. File không đổi so với lần nạp trước thì bỏ qua
            String checksum = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
            List<String> applied = jdbcTemplate.queryForList(
                    "SELECT checksum FROM seed_versions WHERE name = ?", String.class, STATIONS_SEED_NAME);
            if (!applied.isEmpty() && checksum.equals(applied.get(0))) {
                log.info("🏢 [INIT STATIONS] {} unchanged, skip ({} stations in DB)",
                        STATIONS_FILE, rescueStationRepository.count());
                return;
            }

            JsonNode stationsNode = objectMapper.readTree(content).get("stations");
            if (stationsNode == null || !stationsNode.isArray()) {
                log.warn("⚠️ [INIT STATIONS] {} has no \"stations\" array", STATIONS_FILE);
                return;
            }

            List<Object[]> batchArgs = new ArrayList<>(stationsNode.size());
            List<String> codes = new ArrayList<>(stationsNode.size());
            for (JsonNode sn : stationsNode) {
                Object[] row = toStationRow(sn);
                if (row != null) {
                    batchArgs.add(row);
                    codes.add((String) row[0]);
                }
            }

            // 2. File đổi: upsert theo code (giữ nguyên id cũ), xoá trạm không còn trong file,
            //    ghi checksum mới. Tất cả trong 1 transaction, lỗi giữa chừng thì DB giữ dữ liệu cũ.
            transactionTemplate.executeWithoutResult(status -> {
                jdbcTemplate.execute("CREATE UNIQUE INDEX IF NOT EXISTS ux_rescue_stations_code ON rescue_stations (code)");
                jdbcTemplate.batchUpdate(UPSERT_STATION_SQL, batchArgs);
                int removed = jdbcTemplate.update(
                        "DELETE FROM rescue_stations WHERE code IS NULL OR code <> ALL (?)",
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("varchar", codes.toArray())));
                jdbcTemplate.update("INSERT INTO seed_versions (name, checksum, applied_at) VALUES (?, ?, now()) "
                        + "ON CONFLICT (name) DO UPDATE SET checksum = EXCLUDED.checksum, applied_at = now()",
                        STATIONS_SEED_NAME, checksum);
                log.info("🏢 [INIT STATIONS] Synced {} stations from {} ({} removed)",
                        batchArgs.size(), STATIONS_FILE, removed);
            });
        } catch (Exception e) {
            log.error("❌ [INIT STATIONS] Failed to seed rescue stations: {}", e.getMessage(), e);
        }
    }

    /** Một trạm trong JSON thành tham số cho UPSERT_STATION_SQL; thiếu id/tên/toạ độ thì bỏ qua (null). */
    private Object[] toStationRow(JsonNode sn) {
        String code = sn.path("id").asText(null);
        String name = sn.path("name").asText(null);
        double lat = sn.path("lat").asDouble(0);
        double lng = sn.path("lng").asDouble(0);
        if (!StringUtils.hasText(code) || !StringUtils.hasText(name) || lat == 0 || lng == 0) {
            return null;
        }

        String rawAgency = sn.path("agency").asText("police").trim().toUpperCase();
        String agencyType;
        if ("CSGT".equals(rawAgency) || "TRAFFIC_RESCUE".equals(rawAgency)) {
            agencyType = AgencyType.TRAFFIC_RESCUE.name();
        } else if ("FIRE".equals(rawAgency)) {
            agencyType = AgencyType.FIRE.name();
        } else if ("HOSPITAL".equals(rawAgency)) {
            agencyType = AgencyType.HOSPITAL.name();
        } else if ("MILITARY".equals(rawAgency)) {
            agencyType = AgencyType.MILITARY.name();
        } else {
            agencyType = AgencyType.POLICE.name();
        }

        return new Object[] {
                code,
                name.length() > 200 ? name.substring(0, 200) : name,
                agencyType,
                normalizePhoneNumber(sn.path("phone").asText(null)),
                sn.path("address").asText(null),
                sn.path("province").asText(null),
                sn.path("ward").asText(null),
                lat,
                lng,
                lng, // ST_MakePoint(lng, lat)
                lat
        };
    }

    private String normalizePhoneNumber(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return null;
        }
        // Lấy số đầu tiên nếu có nhiều số (ngăn cách bởi ; hoặc /)
        String first = rawPhone.split("[;/]")[0].trim();
        // Chỉ giữ lại các chữ số 0-9
        String digits = first.replaceAll("[^0-9]", "");
        if (digits.isEmpty()) {
            return null;
        }
        // Thay mã quốc gia 84 thành 0 nếu có
        if (digits.startsWith("84") && digits.length() >= 10) {
            digits = "0" + digits.substring(2);
        } else if (!digits.startsWith("0") && digits.length() == 9) {
            digits = "0" + digits;
        }
        // Đưa về giới hạn tối đa 10 ký tự
        if (digits.length() > 10) {
            digits = digits.substring(0, 10);
        }
        return digits;
    }
}