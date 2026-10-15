package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
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

    // Dispatcher role removed; no dispatcher password needed.

    @Override
    public void run(String... args) throws Exception {
        seedRoles();
        seedDefaultAccounts();
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
                    .province("TP. Hà Nội")
                    .ward("Phường Hàng Trống")
                    .isActive(true)
                    .build();
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Seeded Admin Account: admin@sos.vn");
        } else if (!passwordEncoder.matches(adminPassword, admin.getPassword())) {
            admin.setPassword(passwordEncoder.encode(adminPassword));
            accountRepository.save(admin);
            log.info("👑 [INIT ACCOUNT] Synchronized Admin Account password for: admin@sos.vn");
        }

        // Dispatcher role removed; no dispatcher account is seeded.
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