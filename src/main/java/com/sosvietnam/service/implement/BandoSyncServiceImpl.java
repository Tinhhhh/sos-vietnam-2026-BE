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

    @Transactional
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
                throw new RuntimeException("Dữ liệu trả về không hợp lệ");
            }

            int countUpdated = 0;
            int countNew = 0;
            LocalDateTime now = LocalDateTime.now();

            for (JsonNode item : rootNode) {
                String magoc = item.path("magoc").asText("");
                if ("0".equals(magoc)) continue; // Bỏ qua tỉnh, chỉ quét xã/phường

                String malk = item.path("malk").asText("");
                String ten = item.path("ten").asText("");
                String truocSapNhap = item.path("truocsapnhap").asText("");

                Optional<Ward> existingOpt = wardRepository.findByMalk(malk);
                if (existingOpt.isPresent()) {
                    Ward ward = existingOpt.get();
                    if (!Objects.equals(ward.getSapNhapTu(), truocSapNhap) || !Objects.equals(ward.getName(), ten)) {
                        ward.setName(ten);
                        ward.setSapNhapTu(truocSapNhap);
                        ward.setLastSyncedAt(now);
                        wardRepository.save(ward);
                        countUpdated++;
                    }
                }
            }

            this.updatedCount = countUpdated;
            this.lastSyncTime = now;
            this.lastStatus = "SUCCESS";
            this.isSyncing = false;

            log.info("✅ [BANDO SYNC] Hoàn tất đồng bộ: Đã cập nhật {} xã/phường mới nhất.", countUpdated);

            result.put("ok", true);
            result.put("message", "Đồng bộ thành công từ CSDL Quốc gia 2026");
            result.put("updatedCount", countUpdated);
            result.put("lastSyncTime", now.toString());
            return result;

        } catch (Exception e) {
            log.error("❌ [BANDO SYNC ERROR]: {}", e.getMessage());
            this.lastStatus = "ERROR";
            this.isSyncing = false;

            result.put("ok", false);
            result.put("message", "Lỗi đồng bộ: " + e.getMessage());
            return result;
        }
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
