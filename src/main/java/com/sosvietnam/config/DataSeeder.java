package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Province;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.entity.SeedVersion;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.StationLevel;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.util.AreaNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Dữ liệu khởi tạo, chạy mỗi lần BE khởi động, sau khi Flyway đã dựng schema và sau
 * ProvinceDataSeeder (1) / WardDataSeeder (2) vì tài khoản và trạm cần tỉnh/xã có sẵn.
 * Chỉ ghi dữ liệu: đổi bảng / cột / index / sửa dữ liệu cũ một lần thì viết file Flyway mới.
 */
@Component
@Order(3)
@Slf4j
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final AccountRepository accountRepository;
    private final RescueStationRepository rescueStationRepository;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;

    private static final String TEST_ACCOUNTS_FILE = "data/test_accounts.json";
    private static final String ADMIN_EMAIL = "admin@sos.vn";
    private static final String ADMIN_PROVINCE = "Hà Nội";
    private static final String ADMIN_WARD = "Phường Hoàn Kiếm";
    private static final String STATIONS_FILE = "data/rescue_stations.json";
    private static final String STATIONS_SEED_NAME = SeedVersion.RESCUE_STATIONS;
    // Tăng số này khi đổi cách đọc file (thêm cột, đổi mapping) để lần khởi động sau nạp lại dù file không đổi
    private static final String STATIONS_SEED_FORMAT = "5";
    private static final String PLACEHOLDER = "Đang cập nhật";
    // Xã / tỉnh của trạm theo toạ độ (điểm nằm trong xã nào); không khớp xã thì tỉnh theo tên trong file.
    // location do DB tự sinh từ latitude/longitude (generated column) nên không ghi.
    // Tên tỉnh -> khoá so sánh (bỏ dấu, chữ thường, bỏ "TP." / "Tỉnh"), giống AreaNames.provinceKey
    private static final String PROVINCE_KEY_SQL =
            "regexp_replace(trim(regexp_replace(unaccent(lower(%s)), '\\s+', ' ', 'g')), '^(tp\\.?|thanh pho|tinh) ', '')";
    private static final String POINT_SQL = "ST_SetSRID(ST_MakePoint(?, ?), 4326)";
    private static final String UPSERT_STATION_SQL =
            "INSERT INTO rescue_stations (code, name, agency_type, level, phone, address, province_id, ward_id, "
            + "officer, email, services, facility_type, latitude, longitude) "
            + "VALUES (?, ?, ?, ?, ?, ?, "
            + "COALESCE((SELECT w.province_id FROM wards w WHERE ST_Contains(w.geometry, " + POINT_SQL + ") LIMIT 1), "
            + "(SELECT p.code FROM provinces p WHERE " + PROVINCE_KEY_SQL.formatted("p.name") + " = "
            + PROVINCE_KEY_SQL.formatted("?") + " LIMIT 1)), "
            + "(SELECT w.id FROM wards w WHERE ST_Contains(w.geometry, " + POINT_SQL + ") LIMIT 1), "
            + "?, ?, ?, ?, ?, ?) "
            + "ON CONFLICT (code) DO UPDATE SET name = EXCLUDED.name, agency_type = EXCLUDED.agency_type, "
            + "level = EXCLUDED.level, phone = EXCLUDED.phone, address = EXCLUDED.address, "
            + "province_id = EXCLUDED.province_id, ward_id = EXCLUDED.ward_id, officer = EXCLUDED.officer, "
            + "email = EXCLUDED.email, services = EXCLUDED.services, facility_type = EXCLUDED.facility_type, "
            + "latitude = EXCLUDED.latitude, longitude = EXCLUDED.longitude";

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
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByRoleName(roleName.getRole())) {
                roleRepository.save(Role.builder()
                        .roleName(roleName.getRole())
                        .description(roleName.getDescription())
                        .build());
                log.info("🛡️ [INIT ROLE] Seeded role: {}", roleName.getRole());
            }
        }
    }

    /**
     * admin@sos.vn = Trung tâm chỉ huy: cấp Bộ, không thuộc lực lượng nào, trụ sở Phường Hoàn Kiếm (Hà Nội).
     * Có rồi thì đồng bộ mật khẩu với .env và sửa lại cấp / lực lượng / địa bàn nếu lệch.
     */
    private void seedDefaultAccounts() {
        if (!StringUtils.hasText(adminPassword)) {
            log.warn("⚠️ [INIT ACCOUNT] admin@sos.vn not seeded: set SEED_ADMIN_PASSWORD in .env");
            return;
        }
        Province hanoi = findProvince(ADMIN_PROVINCE);
        Ward headquarters = findWard(hanoi, ADMIN_WARD);
        Account admin = accountRepository.findByEmail(ADMIN_EMAIL).orElse(null);
        if (admin == null) {
            Role adminRole = roleRepository.findByRoleName(RoleName.ADMIN.getRole())
                    .orElseThrow(() -> new IllegalStateException("Role " + RoleName.ADMIN.getRole() + " chưa được khởi tạo"));
            admin = Account.builder()
                    .firstName("Quản Trị")
                    .lastName("Hệ Thống")
                    .email(ADMIN_EMAIL)
                    .password(passwordEncoder.encode(adminPassword))
                    .phone("0988888888")
                    .address("Bộ Chỉ Huy SOS Việt Nam")
                    .role(adminRole)
                    .unitLevel(UnitLevel.NATIONAL)
                    .province(hanoi)
                    .ward(headquarters)
                    .isActive(true)
                    .isLocked(false)
                    .build();
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Seeded Admin Account: {}", ADMIN_EMAIL);
            return;
        }

        boolean changed = false;
        if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            changed = true;
            log.info("👑 [INIT ACCOUNT] Synchronized Admin Account password for: {}", ADMIN_EMAIL);
        }
        if (admin.getUnitLevel() != UnitLevel.NATIONAL || admin.getAgencyType() != null) {
            admin.setUnitLevel(UnitLevel.NATIONAL);
            admin.setAgencyType(null);
            changed = true;
        }
        if (admin.getProvince() == null && hanoi != null) {
            admin.setProvince(hanoi);
            changed = true;
        }
        if (admin.getWard() == null && headquarters != null) {
            admin.setWard(headquarters);
            changed = true;
        }
        if (changed) {
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Updated {} (level / agency / area)", ADMIN_EMAIL);
        }
    }

    /**
     * Nạp tài khoản trực ban test từ data/test_accounts.json (theo email): chưa có thì tạo,
     * có rồi thì cập nhật lực lượng / cấp / địa bàn và đồng bộ mật khẩu với .env.
     * "ward" bắt buộc (cấp tỉnh = xã đặt trụ sở), "address" chỉ là số nhà / đường.
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
            Role officerRole = roleRepository.findByRoleName(RoleName.OFFICER.getRole())
                    .orElseThrow(() -> new IllegalStateException("Role " + RoleName.OFFICER.getRole() + " chưa được khởi tạo"));

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

                Province province = findProvince(textOrNull(node, "province"));
                Ward ward = findWard(province, textOrNull(node, "ward"));
                if (province == null || ward == null) {
                    log.warn("⚠️ [INIT TEST ACCOUNTS] Skipped {}: province/ward not found ({} / {})", email,
                            textOrNull(node, "province"), textOrNull(node, "ward"));
                    continue;
                }

                Account account = accountRepository.findByEmail(email).orElse(null);
                boolean isNew = account == null;
                if (isNew) {
                    account = Account.builder().email(email).role(officerRole).isActive(true).isLocked(false).build();
                }
                account.setLastName(textOrNull(node, "last_name"));
                account.setFirstName(textOrNull(node, "first_name"));
                account.setAgencyType(agency);
                account.setUnitLevel(level);
                account.setProvince(province);
                account.setWard(ward);
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

    /** Tỉnh theo tên ("Cần Thơ", "TP. Cần Thơ"...). */
    private Province findProvince(String name) {
        String key = AreaNames.provinceKey(name);
        if (key == null) return null;
        return provinceRepository.findAll().stream()
                .filter(p -> key.equals(AreaNames.provinceKey(p.getName())))
                .findFirst().orElse(null);
    }

    /** Xã theo tên trong tỉnh ("Phường Tân An", "phuong tan an"...). */
    private Ward findWard(Province province, String name) {
        String key = AreaNames.key(name);
        if (province == null || key == null) return null;
        return wardRepository.findByProvince_Code(province.getCode()).stream()
                .filter(w -> key.equals(AreaNames.key(w.getName())))
                .findFirst().orElse(null);
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

            // 1. File không đổi so với lần nạp trước thì bỏ qua (chỉ ghi lại thời điểm kiểm tra)
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(STATIONS_SEED_FORMAT.getBytes(StandardCharsets.UTF_8));
            String checksum = HexFormat.of().formatHex(digest.digest(content));
            List<String> applied = jdbcTemplate.queryForList(
                    "SELECT checksum FROM seed_versions WHERE name = ?", String.class, STATIONS_SEED_NAME);
            if (!applied.isEmpty() && checksum.equals(applied.get(0))) {
                jdbcTemplate.update("UPDATE seed_versions SET checked_at = now(), "
                        + "version = COALESCE(version, to_char(applied_at AT TIME ZONE 'Asia/Ho_Chi_Minh', 'YYYY.MM.DD')) "
                        + "WHERE name = ?", STATIONS_SEED_NAME);
                log.info("🏢 [INIT STATIONS] {} unchanged, skip ({} stations in DB)",
                        STATIONS_FILE, rescueStationRepository.count());
                return;
            }

            JsonNode root = objectMapper.readTree(content);
            // "version" ở đầu file nếu người cập nhật dữ liệu có ghi, không thì lấy ngày nạp
            String version = StringUtils.hasText(root.path("version").asText(null))
                    ? root.path("version").asText()
                    : SeedVersion.versionOf(Instant.now());
            JsonNode stationsNode = root.get("stations");
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
                jdbcTemplate.batchUpdate(UPSERT_STATION_SQL, batchArgs);
                int removed = jdbcTemplate.update(
                        "DELETE FROM rescue_stations WHERE code IS NULL OR code <> ALL (?)",
                        ps -> ps.setArray(1, ps.getConnection().createArrayOf("varchar", codes.toArray())));
                jdbcTemplate.update("INSERT INTO seed_versions (name, checksum, version, applied_at, checked_at) "
                        + "VALUES (?, ?, ?, now(), now()) ON CONFLICT (name) DO UPDATE SET checksum = EXCLUDED.checksum, "
                        + "version = EXCLUDED.version, applied_at = now(), checked_at = now()",
                        STATIONS_SEED_NAME, checksum, version);
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

        StationLevel level = toStationLevel(sn.path("level").asText(null));
        return new Object[] {
                code,
                truncate(name, 200),
                toAgencyType(sn.path("agency").asText("police")).name(),
                level == null ? null : level.name(),
                normalizePhoneNumber(sn.path("phone").asText(null)),
                sn.path("address").asText(null),
                lng, lat,                               // province: ward containing the point (ST_MakePoint(lng, lat))
                sn.path("province").asText(null),       // ... else province by name
                lng, lat,                               // ward containing the point
                truncate(cleanText(sn, "officer"), 200),
                truncate(cleanText(sn, "email"), 150),
                cleanText(sn, "services"),
                truncate(cleanText(sn, "facility_type"), 100),
                lat,
                lng
        };
    }

    /**
     * "agency" trong file: police | csgt | fire | hospital | traffic-rescue.
     * Các trạm cứu hộ giao thông (traffic-rescue) chưa chuẩn hoá: tạm tính là CSGT.
     */
    static AgencyType toAgencyType(String rawAgency) {
        return switch (rawAgency.trim().toUpperCase()) {
            case "CSGT", "TRAFFIC-RESCUE", "TRAFFIC_RESCUE" -> AgencyType.CSGT;
            case "FIRE" -> AgencyType.FIRE;
            case "HOSPITAL" -> AgencyType.HOSPITAL;
            default -> AgencyType.POLICE;
        };
    }

    /** "level" trong file: national | province | ward | enterprise; giá trị lạ lưu null. */
    static StationLevel toStationLevel(String rawLevel) {
        if (!StringUtils.hasText(rawLevel)) {
            return null;
        }
        try {
            return StationLevel.valueOf(rawLevel.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Giá trị chữ của field, null khi trống hoặc là "Đang cập nhật". */
    private static String cleanText(JsonNode node, String field) {
        String value = textOrNull(node, field);
        return value == null || value.equalsIgnoreCase(PLACEHOLDER) ? null : value;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
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