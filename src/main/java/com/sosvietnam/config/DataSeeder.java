package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
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
        // Always refresh rescue stations data on startup
        try {
            long currentCount = rescueStationRepository.count();
            if (currentCount > 0) {
                log.warn("⚠️ [INIT STATIONS] Existing {} stations found. Truncating to reload fresh data.",
                        currentCount);
                jdbcTemplate.execute("TRUNCATE TABLE rescue_stations RESTART IDENTITY");
            }

            ClassPathResource resource = new ClassPathResource("data/rescue_stations.json");
            if (!resource.exists()) {
                log.warn("⚠️ [INIT STATIONS] data/rescue_stations.json not found in classpath");
                return;
            }
            JsonNode rootNode = objectMapper.readTree(resource.getInputStream());
            JsonNode stationsNode = rootNode.get("stations");
            if (stationsNode == null || !stationsNode.isArray()) {
                return;
            }

            String sql = "INSERT INTO rescue_stations (name, agency_type, phone, address, province, ward, latitude, longitude, location) "
                    +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ST_SetSRID(ST_MakePoint(?, ?), 4326))";

            List<Object[]> batchArgs = new ArrayList<>(1000);
            int totalSaved = 0;

            log.info("🏢 [INIT STATIONS] Bắt đầu nạp {} trạm cứu hộ bằng JDBC Batch...", stationsNode.size());

            for (JsonNode sn : stationsNode) {
                String name = sn.path("name").asText(null);
                if (name == null || name.isBlank())
                    continue;
                double lat = sn.path("lat").asDouble(0);
                double lng = sn.path("lng").asDouble(0);
                if (lat == 0 || lng == 0)
                    continue;

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

                String rawPhone = sn.path("phone").asText(null);
                String phone = normalizePhoneNumber(rawPhone);
                String safeName = name.length() > 200 ? name.substring(0, 200) : name;
                String address = sn.path("address").asText(null);
                String province = sn.path("province").asText(null);
                String ward = sn.path("ward").asText(null);

                batchArgs.add(new Object[] {
                        safeName,
                        agencyType,
                        phone,
                        address,
                        province,
                        ward,
                        lat,
                        lng,
                        lng, // ST_MakePoint(lng, lat)
                        lat
                });

                if (batchArgs.size() >= 1000) {
                    jdbcTemplate.batchUpdate(sql, batchArgs);
                    totalSaved += batchArgs.size();
                    log.info("🏢 [INIT STATIONS] Đã nạp {} / {} trạm cứu hộ...", totalSaved, stationsNode.size());
                    batchArgs.clear();
                }
            }

            if (!batchArgs.isEmpty()) {
                jdbcTemplate.batchUpdate(sql, batchArgs);
                totalSaved += batchArgs.size();
                batchArgs.clear();
            }

            log.info("🏢 [INIT STATIONS] Hoàn tất nạp thành công {} trạm cứu hộ vào cơ sở dữ liệu!", totalSaved);
        } catch (Exception e) {
            log.error("❌ [INIT STATIONS] Failed to seed rescue stations: {}", e.getMessage(), e);
        }
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