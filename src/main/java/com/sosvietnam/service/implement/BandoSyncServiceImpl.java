package com.sosvietnam.service.implement;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.BandoSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class BandoSyncServiceImpl implements BandoSyncService {

    private final WardRepository wardRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    @Value("${app.bando.source-url:https://sapnhap.bando.com.vn}")
    private String bandoSourceUrl;

    private boolean isSyncing = false;
    private LocalDateTime lastSyncTime = LocalDateTime.now();
    private String lastStatus = "IDLE";
    private int updatedCount = 0;

    @Scheduled(cron = "${app.bando.sync-cron:0 0 */6 * * *}")
    public void scheduledSync() {
        log.info("📡 [BANDO SYNC] Starting scheduled synchronization with {}", bandoSourceUrl);
        syncFromNationalDatabase();
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
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(bandoSourceUrl + "/p.co_dvhc"))
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .header("User-Agent", "SOS-Emergency-Vietnam-Sync/3.5")
                    .header("Referer", bandoSourceUrl + "/")
                    .timeout(Duration.ofSeconds(20))
                    .POST(HttpRequest.BodyPublishers.ofString("ma=0"))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Máy chủ bando.com.vn phản hồi mã HTTP: " + response.statusCode());
            }

            JsonNode rootNode = objectMapper.readTree(response.body());
            if (!rootNode.isArray()) {
                throw new RuntimeException("Dữ liệu trả về không hợp lệ từ máy chủ bản đồ");
            }

            // 1. Tải toàn bộ Wards vào in-memory HashMap (chỉ 1 câu SELECT)
            List<Ward> allWards = wardRepository.findAll();
            Map<String, Ward> wardMap = new HashMap<>(allWards.size() * 2);
            for (Ward ward : allWards) {
                if (ward.getMalk() != null) {
                    wardMap.put(normalizeMalk(ward.getMalk()), ward);
                    wardMap.put(ward.getMalk().toLowerCase(), ward);
                }
            }

            List<Ward> toUpdate = new ArrayList<>();
            LocalDateTime now = LocalDateTime.now();

            // 2. Đối soát dữ liệu cực nhanh trong RAM (O(1))
            for (JsonNode item : rootNode) {
                String magoc = item.path("magoc").asText("");
                if ("0".equals(magoc)) continue; // Bỏ qua cấp tỉnh, chỉ quét xã/phường

                String malk = item.path("malk").asText("");
                String ten = item.path("ten").asText("").trim();
                String truocSapNhap = item.path("truocsapnhap").asText("").trim();

                Ward ward = wardMap.get(normalizeMalk(malk));
                if (ward != null) {
                    boolean changed = false;
                    if (!ten.isEmpty() && !Objects.equals(ward.getName(), ten)) {
                        ward.setName(ten);
                        changed = true;
                    }
                    if (!truocSapNhap.isEmpty() && !Objects.equals(ward.getSapNhapTu(), truocSapNhap)) {
                        ward.setSapNhapTu(truocSapNhap);
                        changed = true;
                    }
                    if (changed) {
                        ward.setLastSyncedAt(now);
                        toUpdate.add(ward);
                    }
                }
            }

            // 3. Batch Update toàn bộ thay đổi cùng lúc (chỉ 1 lần lưu)
            if (!toUpdate.isEmpty()) {
                wardRepository.saveAll(toUpdate);
            }

            int countUpdated = toUpdate.size();
            this.updatedCount = countUpdated;
            this.lastSyncTime = now;
            this.lastStatus = "SUCCESS";
            this.isSyncing = false;

            log.info("✅ [BANDO SYNC] Hoàn tất đồng bộ: Đã cập nhật {}/{} xã/phường mới nhất.", countUpdated, allWards.size());

            result.put("ok", true);
            result.put("message", "Đồng bộ thành công từ CSDL Quốc gia");
            result.put("totalWardsInSystem", allWards.size());
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
        return status;
    }
}
