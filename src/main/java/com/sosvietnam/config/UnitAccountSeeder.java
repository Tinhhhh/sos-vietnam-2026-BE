package com.sosvietnam.config;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.PoliceRank;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.enums.UnitRole;
import com.sosvietnam.util.AreaNames;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Tài khoản đơn vị cho mọi xã và tỉnh (CHỈ ĐỂ DEMO, dữ liệu cán bộ là tên giả):
 * Công an, CSGT, PCCC ở mọi cấp lực lượng đó có (AgencyType.supports). Mỗi đơn vị 3 cán bộ: chỉ huy, trực ban
 * (có lượt trực ban đang mở), cán bộ.
 * - Email: tỉnh {@code <lực lượng>.<tỉnh>@sos.vn}, xã {@code <lực lượng>.<xã>.<tỉnh>@sos.vn} (congan / csgt / pccc,
 *   không dấu, viết liền); trùng tên trong tỉnh thì thêm id xã.
 * - Mật khẩu đơn vị = SEED_TEST_ACCOUNT_PASSWORD, PIN mọi cán bộ seed = SEED_MEMBER_PIN (trống 1 trong 2 = bỏ qua).
 * - Chạy mỗi lần khởi động nhưng chỉ thêm cái còn thiếu: đơn vị chưa có email thì tạo; đơn vị chưa từng có
 *   trực ban thì thêm 3 cán bộ (sửa / xoá cán bộ sau đó không bị seed đè).
 * - Tài khoản cán bộ có từ trước (unit_account = false): trùng email định dạng mới thì dùng luôn làm tài khoản
 *   đơn vị; còn lại chuyển cán bộ của nó sang đơn vị mới cùng lực lượng / cấp / địa bàn rồi xoá tài khoản cũ
 *   (cột "ai làm" trỏ unit_members nên lịch sử ca không đổi).
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class UnitAccountSeeder implements CommandLineRunner {

    static final List<AgencyType> AGENCIES = List.of(AgencyType.POLICE, AgencyType.CSGT, AgencyType.FIRE);
    private static final Map<AgencyType, String> EMAIL_PREFIX =
            Map.of(AgencyType.POLICE, "congan", AgencyType.CSGT, "csgt", AgencyType.FIRE, "pccc");
    private static final String DOMAIN = "@sos.vn";
    private static final Pattern WARD_PREFIX = Pattern.compile("^(phuong|xa|dac khu|thi tran)\\s+");
    private static final Pattern NOT_SLUG = Pattern.compile("[^a-z0-9]");

    private static final String[] HO = {"Nguyễn", "Trần", "Lê", "Phạm", "Hoàng", "Huỳnh", "Phan", "Vũ", "Võ", "Đặng",
            "Bùi", "Đỗ", "Hồ", "Ngô", "Dương", "Lý", "Đinh", "Trương", "Lâm", "Mai"};
    private static final String[] DEM_NAM = {"Văn", "Hữu", "Minh", "Quốc", "Thanh", "Đức", "Công", "Ngọc", "Tuấn",
            "Hoàng", "Xuân", "Thành"};
    private static final String[] DEM_NU = {"Thị", "Ngọc", "Thu", "Thanh", "Minh", "Phương", "Kim", "Hồng"};
    private static final String[] TEN_NAM = {"An", "Bình", "Cường", "Dũng", "Hải", "Hùng", "Khang", "Long", "Minh",
            "Nam", "Phong", "Quân", "Sơn", "Tài", "Thắng", "Toàn", "Trung", "Tuấn", "Việt", "Vinh", "Hiếu", "Khoa",
            "Lâm", "Phúc", "Thịnh", "Đạt", "Hưng", "Kiên", "Lộc", "Nghĩa"};
    private static final String[] TEN_NU = {"Anh", "Châu", "Diệp", "Hà", "Hạnh", "Hoa", "Hương", "Lan", "Linh", "Mai",
            "My", "Ngân", "Nhung", "Phương", "Thảo", "Trang", "Uyên", "Vy", "Yến", "Hằng"};

    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;

    @Value("${app.seed.test-account-password:}")
    private String unitPassword;

    @Value("${app.seed.member-pin:}")
    private String memberPin;

    /** Một đơn vị cần có: wardId = xã (cấp xã) hoặc xã đặt trụ sở (cấp tỉnh). */
    record Unit(String email, String name, AgencyType agency, UnitLevel level, String provinceId, Long wardId) {
        String key() {
            return key(agency, level, provinceId, level == UnitLevel.WARD ? wardId : null);
        }

        static String key(AgencyType agency, UnitLevel level, String provinceId, Long wardId) {
            return agency + "|" + level + "|" + provinceId + "|" + (wardId == null ? "" : wardId);
        }
    }

    @Override
    public void run(String... args) {
        if (!StringUtils.hasText(unitPassword) || !StringUtils.hasText(memberPin)) {
            log.info("[INIT UNITS] Skipped: set SEED_TEST_ACCOUNT_PASSWORD and SEED_MEMBER_PIN in .env to seed unit accounts");
            return;
        }
        try {
            long started = System.currentTimeMillis();
            List<Unit> units = plan();
            int[] counts = transactionTemplate.execute(status -> {
                String pinHash = passwordEncoder.encode(memberPin);
                int created = createMissing(units);
                int staffed = staffNewUnits(units, pinHash);
                int merged = mergeLegacy(units, pinHash);
                return new int[]{created, staffed, merged};
            });
            log.info("[INIT UNITS] {} units planned: {} accounts created, {} units staffed, {} old accounts merged ({} ms)",
                    units.size(), counts[0], counts[1], counts[2], System.currentTimeMillis() - started);
        } catch (Exception e) {
            log.error("❌ [INIT UNITS] Failed to seed unit accounts: {}", e.getMessage(), e);
        }
    }

    // ---------------------------------------------------------------- danh sách đơn vị cần có

    List<Unit> plan() {
        Map<String, String> provinces = new LinkedHashMap<>();
        jdbc.query("SELECT code, name FROM provinces ORDER BY name",
                rs -> { provinces.put(rs.getString(1), rs.getString(2)); });
        record WardRow(long id, String name, String provinceId) {
        }
        List<WardRow> wards = jdbc.query("SELECT id, name, province_id FROM wards WHERE province_id IS NOT NULL ORDER BY id",
                (rs, i) -> new WardRow(rs.getLong(1), rs.getString(2), rs.getString(3)));
        // trụ sở cấp tỉnh: xã của trạm cấp tỉnh cùng lực lượng (nếu có), không thì xã đầu tiên theo tên
        Map<String, Long> headquarters = new HashMap<>();
        jdbc.query("""
                SELECT DISTINCT ON (province_id, agency_type) province_id, agency_type, ward_id
                FROM rescue_stations
                WHERE ward_id IS NOT NULL AND province_id IS NOT NULL AND level = 'PROVINCE'
                ORDER BY province_id, agency_type, id
                """, rs -> { headquarters.put(rs.getString(1) + "|" + rs.getString(2), rs.getLong(3)); });
        Map<String, Long> firstWard = new HashMap<>();
        wards.stream().sorted(Comparator.comparing(WardRow::name))
                .forEach(w -> firstWard.putIfAbsent(w.provinceId(), w.id()));

        List<Unit> units = new ArrayList<>();
        Set<String> emails = new HashSet<>();
        for (AgencyType agency : AGENCIES) {
            String prefix = EMAIL_PREFIX.get(agency);
            provinces.forEach((code, name) -> {
                if (!agency.supports(UnitLevel.PROVINCE)) return;
                Long hq = headquarters.getOrDefault(code + "|" + agency.name(), firstWard.get(code));
                String email = prefix + "." + provinceSlug(name) + DOMAIN;
                if (emails.add(email)) {
                    units.add(new Unit(email, unitName(agency, UnitLevel.PROVINCE, name), agency, UnitLevel.PROVINCE, code, hq));
                }
            });
            if (!agency.supports(UnitLevel.WARD)) continue;
            for (WardRow w : wards) {
                String province = provinceSlug(provinces.get(w.provinceId()));
                String email = prefix + "." + wardSlug(w.name()) + "." + province + DOMAIN;
                if (!emails.add(email)) {
                    email = prefix + "." + wardSlug(w.name()) + w.id() + "." + province + DOMAIN;
                    emails.add(email);
                }
                units.add(new Unit(email, unitName(agency, UnitLevel.WARD, w.name()), agency, UnitLevel.WARD, w.provinceId(), w.id()));
            }
        }
        return units;
    }

    static String provinceSlug(String name) {
        String key = AreaNames.provinceKey(name);
        return key == null ? "" : NOT_SLUG.matcher(key).replaceAll("");
    }

    static String wardSlug(String name) {
        String key = AreaNames.key(name);
        return key == null ? "" : NOT_SLUG.matcher(WARD_PREFIX.matcher(key).replaceFirst("")).replaceAll("");
    }

    static String unitName(AgencyType agency, UnitLevel level, String area) {
        return switch (agency) {
            case POLICE -> "Công an " + area;
            case CSGT -> (level == UnitLevel.WARD ? "Đội CSGT " : "Phòng CSGT ") + area;
            case FIRE -> (level == UnitLevel.WARD ? "Đội PCCC & CNCH " : "Phòng Cảnh sát PCCC & CNCH ") + area;
            default -> agency.getLabel() + " " + area;
        };
    }

    // ---------------------------------------------------------------- tài khoản đơn vị

    /** Tạo email còn thiếu; email đã có ở tài khoản cũ (vd congan.cantho@sos.vn) thì nâng thành tài khoản đơn vị. */
    private int createMissing(List<Unit> units) {
        Map<String, Boolean> existing = new HashMap<>();
        jdbc.query("SELECT lower(email), unit_account FROM accounts", rs -> { existing.put(rs.getString(1), rs.getBoolean(2)); });
        Long roleId = jdbc.queryForObject("SELECT role_id FROM roles WHERE role_name = ?", Long.class, RoleName.OFFICER.getRole());
        String passwordHash = passwordEncoder.encode(unitPassword);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());

        List<Object[]> inserts = new ArrayList<>();
        for (Unit u : units) {
            Boolean unitAccount = existing.get(u.email());
            if (unitAccount == null) {
                inserts.add(new Object[]{UUID.randomUUID(), u.email(), passwordHash, u.name(), roleId, u.agency().name(),
                        u.level().name(), u.provinceId(), u.wardId(), now});
            } else if (!unitAccount) {
                jdbc.update("""
                        UPDATE accounts SET unit_account = true, password = ?, first_name = ?, last_name = NULL,
                               agency_type = ?, unit_level = ?, province_id = ?, ward_id = ?, is_active = true
                        WHERE lower(email) = ?
                        """, passwordHash, u.name(), u.agency().name(), u.level().name(), u.provinceId(), u.wardId(), u.email());
                log.info("[INIT UNITS] {} -> tài khoản đơn vị", u.email());
            }
        }
        jdbc.batchUpdate("""
                INSERT INTO accounts (account_id, email, password, first_name, role_id, agency_type, unit_level,
                                      province_id, ward_id, created_date, is_active, is_locked, unit_account)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, false, true)
                """, inserts);
        return inserts.size();
    }

    /** Đơn vị chưa từng có trực ban: thêm chỉ huy + trực ban + cán bộ (tên giả) và mở lượt trực ban. */
    private int staffNewUnits(List<Unit> units, String pinHash) {
        Map<String, Unit> byEmail = new HashMap<>();
        units.forEach(u -> byEmail.put(u.email(), u));
        List<Map<String, Object>> empty = jdbc.queryForList("""
                SELECT a.account_id, lower(a.email) AS email FROM accounts a
                WHERE a.unit_account AND NOT EXISTS (SELECT 1 FROM unit_duty_assignments d WHERE d.account_id = a.account_id)
                """);
        Timestamp now = Timestamp.valueOf(LocalDateTime.now());
        List<Object[]> members = new ArrayList<>();
        List<Object[]> duties = new ArrayList<>();
        for (Map<String, Object> row : empty) {
            Unit u = byEmail.get((String) row.get("email"));
            if (u == null) continue; // tài khoản đơn vị không còn trong danh sách (xã đã đổi tên...)
            UUID accountId = (UUID) row.get("account_id");
            Random random = new Random(u.email().hashCode());
            Set<String> badges = new HashSet<>();
            boolean province = u.level() == UnitLevel.PROVINCE;
            UUID dutyId = UUID.randomUUID();
            members.add(member(UUID.randomUUID(), accountId, random, badges, UnitRole.COMMANDER,
                    province ? PoliceRank.DAI_TA : PoliceRank.TRUNG_TA, commanderTitle(u), pinHash, now));
            members.add(member(dutyId, accountId, random, badges, UnitRole.MEMBER,
                    province ? PoliceRank.THIEU_TA : PoliceRank.DAI_UY, "Cán bộ trực ban", pinHash, now));
            members.add(member(UUID.randomUUID(), accountId, random, badges, UnitRole.MEMBER,
                    random.nextBoolean() ? PoliceRank.THUONG_UY : PoliceRank.TRUNG_UY, "Cán bộ", pinHash, now));
            duties.add(new Object[]{accountId, dutyId, now});
        }
        jdbc.batchUpdate("""
                INSERT INTO unit_members (member_id, account_id, full_name, badge_number, rank, position, unit_role, phone,
                                          pin_hash, failed_pin_count, active, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, true, ?)
                """, members);
        jdbc.batchUpdate("INSERT INTO unit_duty_assignments (account_id, member_id, started_at) VALUES (?, ?, ?)", duties);
        return duties.size();
    }

    private static Object[] member(UUID id, UUID accountId, Random random, Set<String> badges, UnitRole role,
                                   PoliceRank rank, String position, String pinHash, Timestamp now) {
        String badge;
        do {
            badge = String.format("%06d", random.nextInt(1_000_000));
        } while (!badges.add(badge));
        // Không sinh số điện thoại: số ngẫu nhiên có thể là số thật, SMS (link hiện trường, ca kết thúc) sẽ gửi nhầm người
        return new Object[]{id, accountId, fakeName(random), badge, rank.name(), position, role.name(), null, pinHash, now};
    }

    private static String commanderTitle(Unit u) {
        boolean ward = u.level() == UnitLevel.WARD;
        return switch (u.agency()) {
            case POLICE -> ward ? "Trưởng Công an" : "Giám đốc Công an";
            case CSGT, FIRE -> ward ? "Đội trưởng" : "Trưởng phòng";
            default -> "Chỉ huy";
        };
    }

    static String fakeName(Random random) {
        String ho = HO[random.nextInt(HO.length)];
        if (random.nextInt(4) == 0) {
            return ho + " " + DEM_NU[random.nextInt(DEM_NU.length)] + " " + TEN_NU[random.nextInt(TEN_NU.length)];
        }
        return ho + " " + DEM_NAM[random.nextInt(DEM_NAM.length)] + " " + TEN_NAM[random.nextInt(TEN_NAM.length)];
    }

    // ---------------------------------------------------------------- gộp tài khoản cán bộ cũ

    /** Tài khoản cán bộ cũ: chuyển cán bộ (cùng id, lịch sử ca giữ nguyên) sang đơn vị mới rồi xoá tài khoản cũ. */
    private int mergeLegacy(List<Unit> units, String pinHash) {
        Map<String, String> emailByKey = new HashMap<>();
        units.forEach(u -> emailByKey.put(u.key(), u.email()));
        List<Map<String, Object>> legacy = jdbc.queryForList("""
                SELECT a.account_id, a.email, a.agency_type, a.unit_level, a.province_id, a.ward_id
                FROM accounts a JOIN roles r ON r.role_id = a.role_id
                WHERE r.role_name = ? AND NOT a.unit_account
                """, RoleName.OFFICER.getRole());
        int merged = 0;
        for (Map<String, Object> row : legacy) {
            String email = (String) row.get("email");
            String agency = (String) row.get("agency_type");
            String level = (String) row.get("unit_level");
            if (agency == null || level == null || row.get("province_id") == null) {
                log.warn("[INIT UNITS] {}: thiếu lực lượng / cấp / tỉnh, giữ nguyên", email);
                continue;
            }
            UnitLevel unitLevel = UnitLevel.valueOf(level);
            Long wardId = row.get("ward_id") == null ? null : ((Number) row.get("ward_id")).longValue();
            String target = emailByKey.get(Unit.key(AgencyType.valueOf(agency), unitLevel, (String) row.get("province_id"),
                    unitLevel == UnitLevel.WARD ? wardId : null));
            List<UUID> targetId = target == null ? List.of()
                    : jdbc.queryForList("SELECT account_id FROM accounts WHERE lower(email) = ? AND unit_account", UUID.class, target);
            if (targetId.isEmpty()) {
                log.warn("[INIT UNITS] {}: không tìm thấy đơn vị mới, giữ nguyên", email);
                continue;
            }
            UUID legacyId = (UUID) row.get("account_id");
            jdbc.update("UPDATE unit_members SET account_id = ?, pin_hash = COALESCE(pin_hash, ?) WHERE account_id = ?",
                    targetId.get(0), pinHash, legacyId);
            jdbc.update("DELETE FROM accounts WHERE account_id = ?", legacyId);
            log.info("[INIT UNITS] {} -> cán bộ của {}", email, target);
            merged++;
        }
        // cán bộ của tài khoản cũ (chưa từng có PIN, không có mã kích hoạt): dùng PIN seed để test được ngay
        jdbc.update("""
                UPDATE unit_members m SET pin_hash = ? FROM accounts a
                WHERE a.account_id = m.account_id AND a.unit_account AND m.pin_hash IS NULL AND m.activation_code_hash IS NULL
                """, pinHash);
        return merged;
    }
}
