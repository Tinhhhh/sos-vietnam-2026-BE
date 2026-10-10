package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Ward;
import com.sosvietnam.repository.WardRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Order(2) // Chạy sau khi seed tỉnh thành xong
@Slf4j
@RequiredArgsConstructor
public class WardDataSeeder implements CommandLineRunner {

    private final WardRepository wardRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);
    private static final Pattern CODE_PATTERN = Pattern.compile("^ward-([0-9A-Za-z]+)-");

    @Override
    public void run(String... args) {
        if (wardRepository.count() > 0) {
            log.info("🏘️ [INIT WARDS] Dữ liệu xã/phường đã có sẵn ({} xã/phường). Bỏ qua seeding.", wardRepository.count());
            return;
        }

        try {
            log.info("🏘️ [INIT WARDS] Bắt đầu nạp 3.321 xã/phường từ GeoJSON vào PostgreSQL/PostGIS...");
            seedWards();
        } catch (Exception e) {
            log.error("❌ [INIT WARDS ERROR] Lỗi khi nạp dữ liệu xã/phường: {}", e.getMessage(), e);
        }
    }

    private void seedWards() throws Exception {
        ClassPathResource wardsResource = new ClassPathResource("geo/vn-wards-simplified.geojson");
        if (!wardsResource.exists()) {
            log.warn("⚠️ [INIT WARDS] Không tìm thấy file geo/vn-wards-simplified.geojson trong resources");
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        List<Ward> batch = new ArrayList<>(500);
        int totalSaved = 0;

        try (InputStream is = wardsResource.getInputStream()) {
            JsonNode rootNode = objectMapper.readTree(is);
            JsonNode features = rootNode.path("features");

            for (JsonNode f : features) {
                JsonNode props = f.path("properties");
                String wardName = props.path("ward").asText("").trim();
                String provinceName = props.path("province").asText("").trim();
                String malk = props.path("id").asText("").trim();

                // Xác định mã tỉnh
                String provinceCode = ProvinceDataSeeder.PROVINCE_CODE_MAP.get(provinceName);
                if (provinceCode == null) {
                    Matcher matcher = CODE_PATTERN.matcher(malk);
                    if (matcher.find()) {
                        provinceCode = matcher.group(1);
                    }
                }

                Double centerLat = props.has("lat") && !props.path("lat").isNull() ? props.path("lat").asDouble() : null;
                Double centerLng = props.has("lng") && !props.path("lng").isNull() ? props.path("lng").asDouble() : null;

                Geometry geom = parseGeoJsonGeometry(f.path("geometry"));
                if (geom != null) {
                    geom.setSRID(4326);
                    if (centerLat == null || centerLng == null) {
                        try {
                            Point centroid = geom.getCentroid();
                            centerLat = centroid.getY();
                            centerLng = centroid.getX();
                        } catch (Exception ignored) {}
                    }
                }

                Ward ward = Ward.builder()
                        .name(wardName)
                        .malk(malk)
                        .provinceCode(provinceCode)
                        .provinceName(provinceName)
                        .centerLat(centerLat)
                        .centerLng(centerLng)
                        .geometry(geom)
                        .lastSyncedAt(now)
                        .build();

                batch.add(ward);

                if (batch.size() >= 500) {
                    wardRepository.saveAll(batch);
                    totalSaved += batch.size();
                    log.info("🏘️ [INIT WARDS] Đã nạp {} xã/phường...", totalSaved);
                    batch.clear();
                }
            }

            if (!batch.isEmpty()) {
                wardRepository.saveAll(batch);
                totalSaved += batch.size();
                batch.clear();
            }
        }

        log.info("✅ [INIT WARDS] Đã nạp thành công toàn bộ {} xã/phường vào cơ sở dữ liệu!", totalSaved);
    }

    private Geometry parseGeoJsonGeometry(JsonNode geomNode) {
        String type = geomNode.path("type").asText("");
        JsonNode coordsNode = geomNode.path("coordinates");

        if ("Polygon".equalsIgnoreCase(type)) {
            return parsePolygon(coordsNode);
        } else if ("MultiPolygon".equalsIgnoreCase(type)) {
            return parseMultiPolygon(coordsNode);
        }
        return null;
    }

    private Polygon parsePolygon(JsonNode ringsNode) {
        if (!ringsNode.isArray() || ringsNode.isEmpty()) {
            return null;
        }
        LinearRing shell = parseLinearRing(ringsNode.get(0));
        if (shell == null) return null;

        LinearRing[] holes = null;
        if (ringsNode.size() > 1) {
            holes = new LinearRing[ringsNode.size() - 1];
            for (int i = 1; i < ringsNode.size(); i++) {
                holes[i - 1] = parseLinearRing(ringsNode.get(i));
            }
        }
        return geometryFactory.createPolygon(shell, holes);
    }

    private LinearRing parseLinearRing(JsonNode ringNode) {
        if (!ringNode.isArray() || ringNode.size() < 4) {
            return null;
        }
        Coordinate[] coords = new Coordinate[ringNode.size()];
        for (int i = 0; i < ringNode.size(); i++) {
            JsonNode pt = ringNode.get(i);
            coords[i] = new Coordinate(pt.get(0).asDouble(), pt.get(1).asDouble());
        }

        // Đảm bảo ring khép kín theo chuẩn JTS
        if (!coords[0].equals2D(coords[coords.length - 1])) {
            Coordinate[] closedCoords = Arrays.copyOf(coords, coords.length + 1);
            closedCoords[coords.length] = coords[0];
            coords = closedCoords;
        }

        return geometryFactory.createLinearRing(coords);
    }

    private MultiPolygon parseMultiPolygon(JsonNode multiPolyNode) {
        if (!multiPolyNode.isArray() || multiPolyNode.isEmpty()) {
            return null;
        }
        List<Polygon> polygons = new ArrayList<>();
        for (JsonNode polyNode : multiPolyNode) {
            Polygon poly = parsePolygon(polyNode);
            if (poly != null) {
                polygons.add(poly);
            }
        }
        return geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[0]));
    }
}
