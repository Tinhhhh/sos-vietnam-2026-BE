package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.config.WardDataSeeder;
import com.sosvietnam.model.entity.SeedVersion;
import com.sosvietnam.repository.RescueStationRepository;
import com.sosvietnam.repository.SeedVersionRepository;
import com.sosvietnam.service.BandoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Đồng bộ với sapnhap.bando.com.vn:
 * - Tên xã + "sáp nhập từ" (p.co_dvhc, 6 giờ / lần) -> seed_versions "ward_names".
 * - Ranh giới gốc (chưa rút gọn) của 34 tỉnh + 3.321 xã (pread_json từng đơn vị) -> cột boundary (V25), chạy nền
 *   ~20 phút: lần đầu khởi động khi chưa có, khi danh sách bando đổi, hoặc ADMIN bấm. Xong thì gán lại xã cho trạm.
 *   Cập nhật tại chỗ theo malk / mã tỉnh nên id xã / tỉnh không đổi.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BandoSyncServiceImpl implements BandoSyncService {

    private final SeedVersionRepository seedVersionRepository;
    private final RescueStationRepository rescueStationRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${app.bando.source-url:https://sapnhap.bando.com.vn}")
    private String bandoSourceUrl;

    // Nghỉ giữa 2 lần gọi pread_json, đừng dồn hơn 3.000 request vào máy chủ bando
    @Value("${app.bando.request-delay-ms:300}")
    private long requestDelayMs;

    @Value("${app.bando.boundary-sync-on-start:false}")
    private boolean boundarySyncOnStart;

    private boolean isSyncing = false;
    private LocalDateTime lastSyncTime = LocalDateTime.now();
    private String lastStatus = "IDLE";
    private int updatedCount = 0;

    // Trạng thái lượt tải ranh giới (chạy nền, đọc qua getStatus)
    private final AtomicBoolean boundaryRunning = new AtomicBoolean(false);
    private volatile String boundaryStatus = "IDLE";
    private volatile int boundaryDone = 0;
    private volatile int boundaryTotal = 0;
    private volatile int boundaryFailed = 0;
    private volatile String boundaryError;
    private volatile Instant boundaryStartedAt;
    private volatile Instant boundaryFinishedAt;

    @Scheduled(cron = "${app.bando.sync-cron:0 0 */6 * * *}")
    public void scheduledSync() {
        log.info("📡 [BANDO SYNC] Starting scheduled synchronization with {}", bandoSourceUrl);
        syncFromNationalDatabase();
    }

    /** Lần đầu chạy bản có cột boundary (chưa từng tải ranh giới gốc) thì tự tải. */
    @EventListener(ApplicationReadyEvent.class)
    public void syncBoundariesOnStartIfMissing() {
        if (!boundarySyncOnStart) return;
        SeedVersion seed = seedVersionRepository.findById(SeedVersion.BOUNDARIES).orElse(null);
        if (seed == null || !seed.getChecksum().startsWith(WardDataSeeder.BOUNDARY_SOURCE_BANDO)) {
            log.info("📡 [BANDO BOUNDARY] Original boundaries not loaded yet, starting background sync");
            startBoundarySync();
        }
    }

    @Override
    public synchronized Map<String, Object> syncFromNationalDatabase() {
        Map<String, Object> result = new HashMap<>();
        if (isSyncing) {
            result.put("ok", false);
            result.put("message", "Quá trình đồng bộ đang diễn ra...");
            return result;
        }

        isSyncing = true;
        lastStatus = "SYNCING";

        try {
            String body = fetchUnitList();
            JsonNode rootNode = objectMapper.readTree(body);
            if (!rootNode.isArray()) {
                throw new RuntimeException("Dữ liệu trả về không hợp lệ từ máy chủ bản đồ");
            }

            // 1. Tên xã hiện có (JDBC, không nạp entity Ward để khỏi kéo polygon theo)
            record WardRow(long id, String name, String sapNhapTu) {
            }
            Map<String, WardRow> wardMap = new HashMap<>();
            jdbcTemplate.query("SELECT id, malk, name, sap_nhap_tu FROM wards WHERE malk IS NOT NULL", rs -> {
                wardMap.put(normalizeMalk(rs.getString(2)), new WardRow(rs.getLong(1), rs.getString(3), rs.getString(4)));
            });

            List<Object[]> toUpdate = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            // 2. Đối soát trong RAM
            for (JsonNode item : rootNode) {
                String magoc = item.path("magoc").asText("");
                if ("0".equals(magoc)) continue; // Bỏ qua cấp tỉnh, chỉ quét xã/phường

                String malk = item.path("malk").asText("");
                String ten = item.path("ten").asText("").trim();
                String truocSapNhap = item.path("truocsapnhap").asText("").trim();

                WardRow ward = wardMap.get(normalizeMalk(malk));
                if (ward != null) {
                    String name = ten.isEmpty() ? ward.name() : ten;
                    String sapNhapTu = truocSapNhap.isEmpty() ? ward.sapNhapTu() : truocSapNhap;
                    if (!Objects.equals(ward.name(), name) || !Objects.equals(ward.sapNhapTu(), sapNhapTu)) {
                        toUpdate.add(new Object[] {name, sapNhapTu, Timestamp.valueOf(now), ward.id()});
                    }
                }
            }

            // 3. Ghi một lượt
            if (!toUpdate.isEmpty()) {
                jdbcTemplate.batchUpdate("UPDATE wards SET name = ?, sap_nhap_tu = ?, last_synced_at = ? WHERE id = ?", toUpdate);
            }

            boolean listChanged = recordWardNamesVersion(body);

            int countUpdated = toUpdate.size();
            this.updatedCount = countUpdated;
            this.lastSyncTime = now;
            this.lastStatus = "SUCCESS";
            this.isSyncing = false;

            log.info("✅ [BANDO SYNC] Hoàn tất đồng bộ: Đã cập nhật {}/{} xã/phường mới nhất.", countUpdated, wardMap.size());
            // Danh sách bando đổi (xã mới, nhập / tách, đổi tên) thì ranh giới có thể đổi theo
            if (listChanged) {
                startBoundarySync();
            }

            result.put("ok", true);
            result.put("message", "Đồng bộ thành công từ CSDL Quốc gia");
            result.put("totalWardsInSystem", wardMap.size());
            result.put("updatedCount", countUpdated);
            result.put("lastSyncTime", now.toString());
            return result;

        } catch (Exception e) {
            log.error("❌ [BANDO SYNC ERROR]: {}", e.getMessage(), e);
            this.lastStatus = "ERROR";
            this.isSyncing = false;

            result.put("ok", false);
            result.put("message", "Lỗi đồng bộ: " + e.getMessage());
            return result;
        }
    }

    @Override
    public Map<String, Object> startBoundarySync() {
        if (boundaryRunning.compareAndSet(false, true)) {
            boundaryStatus = "SYNCING";
            boundaryDone = 0;
            boundaryTotal = 0;
            boundaryFailed = 0;
            boundaryError = null;
            boundaryStartedAt = Instant.now();
            boundaryFinishedAt = null;
            Thread.ofPlatform().name("bando-boundary-sync").daemon(true).start(this::runBoundarySync);
        }
        return getStatus();
    }

    private void runBoundarySync() {
        try {
            JsonNode units = objectMapper.readTree(fetchUnitList());
            Map<String, Long> wardIds = new HashMap<>();
            jdbcTemplate.query("SELECT id, malk FROM wards WHERE malk IS NOT NULL",
                    rs -> { wardIds.put(normalizeMalk(rs.getString(2)), rs.getLong(1)); });
            Set<String> provinceCodes = new HashSet<>(jdbcTemplate.queryForList("SELECT code FROM provinces", String.class));
            boundaryTotal = units.size();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            int skipped = 0;

            for (JsonNode unit : units) {
                String malk = unit.path("malk").asText("");
                boolean isProvince = "0".equals(unit.path("magoc").asText(""));
                String provinceCode = unit.path("ma").asText("");
                Long wardId = isProvince ? null : wardIds.get(normalizeMalk(malk));
                if (malk.isEmpty() || (isProvince ? !provinceCodes.contains(provinceCode) : wardId == null)) {
                    skipped++;
                    boundaryDone++;
                    continue;
                }
                String geometry = fetchGeometry(malk);
                if (geometry == null) {
                    boundaryFailed++;
                } else {
                    // ST_MakeValid: polygon lỗi tự cắt làm ST_Contains báo lỗi GEOS
                    if (isProvince) {
                        jdbcTemplate.update("UPDATE provinces SET boundary = ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)) "
                                + "WHERE code = ?", geometry, provinceCode);
                    } else {
                        jdbcTemplate.update("UPDATE wards SET boundary = ST_MakeValid(ST_SetSRID(ST_GeomFromGeoJSON(?), 4326)) "
                                + "WHERE id = ?", geometry, wardId);
                    }
                    digest.update(geometry.getBytes(StandardCharsets.UTF_8));
                }
                boundaryDone++;
                if (boundaryDone % 500 == 0) {
                    log.info("📡 [BANDO BOUNDARY] {}/{} units, {} failed", boundaryDone, boundaryTotal, boundaryFailed);
                }
                Thread.sleep(requestDelayMs);
            }

            Integer stations = transactionTemplate.execute(status -> rescueStationRepository.reassignAreas());
            if (boundaryFailed == 0) {
                recordBoundariesVersion(digest);
            }
            boundaryStatus = boundaryFailed == 0 ? "SUCCESS" : "PARTIAL";
            log.info("✅ [BANDO BOUNDARY] Done: {} units, {} failed, {} not in DB; {} stations re-placed",
                    boundaryTotal, boundaryFailed, skipped, stations);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            boundaryStatus = "ERROR";
            boundaryError = "Interrupted";
        } catch (Exception e) {
            log.error("❌ [BANDO BOUNDARY ERROR]: {}", e.getMessage(), e);
            boundaryStatus = "ERROR";
            boundaryError = e.getMessage();
        } finally {
            boundaryFinishedAt = Instant.now();
            boundaryRunning.set(false);
        }
    }

    /** Danh sách 34 tỉnh + 3.321 xã (ma, ten, magoc, malk, truocsapnhap). */
    private String fetchUnitList() throws Exception {
        HttpResponse<String> response = post("/p.co_dvhc", "ma=0", Duration.ofSeconds(20));
        if (response.statusCode() != 200) {
            throw new RuntimeException("Máy chủ bando.com.vn phản hồi mã HTTP: " + response.statusCode());
        }
        return response.body();
    }

    /** Geometry GeoJSON (WGS84) của 1 tỉnh / xã, thử lại 1 lần; null nếu không lấy được. */
    private String fetchGeometry(String malk) throws InterruptedException {
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                HttpResponse<String> response = post("/pread_json",
                        "id=" + URLEncoder.encode(malk, StandardCharsets.UTF_8), Duration.ofSeconds(30));
                if (response.statusCode() == 200) {
                    JsonNode geometry = objectMapper.readTree(response.body()).path("features").path(0).path("geometry");
                    if (geometry.has("coordinates")) {
                        return geometry.toString();
                    }
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Exception e) {
                log.warn("⚠️ [BANDO BOUNDARY] {} attempt {} failed: {}", malk, attempt, e.getMessage());
            }
            Thread.sleep(1000L * attempt);
        }
        boundaryError = "Không tải được " + malk;
        return null;
    }

    private HttpResponse<String> post(String path, String form, Duration timeout) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(bandoSourceUrl + path))
                .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                .header("User-Agent", "SOS-Emergency-Vietnam-Sync/3.5")
                .header("Referer", bandoSourceUrl + "/")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Ghi seed_versions "ward_names" (tên xã + sáp nhập) cho GET /geo/data-status: checked_at mỗi lần
     * đồng bộ, version + applied_at chỉ đổi khi dữ liệu bando trả về khác lần trước.
     * @return true khi danh sách khác lần trước (không tính lần ghi đầu tiên)
     */
    private boolean recordWardNamesVersion(String body) throws NoSuchAlgorithmException {
        String checksum = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(body.getBytes(StandardCharsets.UTF_8)));
        Instant now = Instant.now();
        SeedVersion seed = seedVersionRepository.findById(SeedVersion.WARD_NAMES).orElse(null);
        boolean changed = seed != null && !checksum.equals(seed.getChecksum());
        if (seed == null || changed) {
            seed = SeedVersion.builder()
                    .name(SeedVersion.WARD_NAMES)
                    .checksum(checksum)
                    .version(SeedVersion.versionOf(now))
                    .appliedAt(now)
                    .build();
        }
        seed.setCheckedAt(now);
        seedVersionRepository.save(seed);
        return changed;
    }

    /**
     * Ghi seed_versions "boundaries" sau lượt tải ranh giới gốc thành công: checksum "bando:..." (cột 64 ký tự),
     * version + applied_at đổi khi ranh giới khác lần trước, checked_at mỗi lượt.
     */
    private void recordBoundariesVersion(MessageDigest digest) {
        String checksum = WardDataSeeder.BOUNDARY_SOURCE_BANDO
                + HexFormat.of().formatHex(digest.digest()).substring(0, 64 - WardDataSeeder.BOUNDARY_SOURCE_BANDO.length());
        Instant now = Instant.now();
        SeedVersion seed = seedVersionRepository.findById(SeedVersion.BOUNDARIES).orElse(null);
        if (seed == null || !checksum.equals(seed.getChecksum())) {
            seed = SeedVersion.builder()
                    .name(SeedVersion.BOUNDARIES)
                    .checksum(checksum)
                    .version(SeedVersion.versionOf(now))
                    .appliedAt(now)
                    .build();
        }
        seed.setCheckedAt(now);
        seedVersionRepository.save(seed);
    }

    /**
     * Chuẩn hóa malk để khớp chính xác giữa GeoJSON ID và Bando API:
     * Ví dụ:
     *   "ward-01-diaphanhanhchinhcapxa_2025_3256" -> "diaphanhanhchinhcapxa_2025.3256"
     *   "diaphanhanhchinhcapxa_2025.3256" -> "diaphanhanhchinhcapxa_2025.3256"
     */
    private String normalizeMalk(String malk) {
        if (malk == null || malk.isBlank()) {
            return "";
        }
        return malk.trim()
                .replaceFirst("^ward-[0-9A-Za-z]+-", "")
                .replaceAll("_(?=[0-9]+$)", ".")
                .toLowerCase();
    }

    @Override
    public Map<String, Object> getStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("isSyncing", isSyncing);
        status.put("lastStatus", lastStatus);
        status.put("lastSyncTime", lastSyncTime != null ? lastSyncTime.toString() : null);
        status.put("sourceUrl", bandoSourceUrl);
        status.put("updatedCount", updatedCount);
        Map<String, Object> boundary = new HashMap<>();
        boundary.put("status", boundaryStatus);
        boundary.put("done", boundaryDone);
        boundary.put("total", boundaryTotal);
        boundary.put("failed", boundaryFailed);
        boundary.put("error", boundaryError);
        boundary.put("startedAt", boundaryStartedAt != null ? boundaryStartedAt.toString() : null);
        boundary.put("finishedAt", boundaryFinishedAt != null ? boundaryFinishedAt.toString() : null);
        status.put("boundary", boundary);
        return status;
    }
}
