package com.sosvietnam.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sosvietnam.model.entity.Province;
import com.sosvietnam.repository.ProvinceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.*;

@Component
@Order(1) // Chạy trước hoặc cùng đợt khởi động với DataSeeder
@Slf4j
@RequiredArgsConstructor
public class ProvinceDataSeeder implements CommandLineRunner {

    private final ProvinceRepository provinceRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // Bảng tra cứu mã định danh hành chính 34 tỉnh/thành phố theo CSDL Quốc Gia
    public static final Map<String, String> PROVINCE_CODE_MAP = Map.ofEntries(
            Map.entry("Hà Nội", "01"),
            Map.entry("Cao Bằng", "04"),
            Map.entry("Tuyên Quang", "08"),
            Map.entry("Điện Biên", "11"),
            Map.entry("Lai Châu", "12"),
            Map.entry("Sơn La", "14"),
            Map.entry("Lào Cai", "15"),
            Map.entry("Thái Nguyên", "19"),
            Map.entry("Lạng Sơn", "20"),
            Map.entry("Quảng Ninh", "22"),
            Map.entry("Bắc Ninh", "24"),
            Map.entry("Phú Thọ", "25"),
            Map.entry("Hải Phòng", "31"),
            Map.entry("Hưng Yên", "33"),
            Map.entry("Ninh Bình", "37"),
            Map.entry("Thanh Hóa", "38"),
            Map.entry("Nghệ An", "40"),
            Map.entry("Hà Tĩnh", "42"),
            Map.entry("Quảng Trị", "44"),
            Map.entry("Huế", "46"),
            Map.entry("Đà Nẵng", "48"),
            Map.entry("Quảng Ngãi", "51"),
            Map.entry("Gia Lai", "52"),
            Map.entry("Khánh Hòa", "56"),
            Map.entry("Đắk Lắk", "66"),
            Map.entry("Lâm Đồng", "68"),
            Map.entry("Đồng Nai", "75"),
            Map.entry("TP. Hồ Chí Minh", "79"),
            Map.entry("Tây Ninh", "80"),
            Map.entry("Đồng Tháp", "82"),
            Map.entry("Vĩnh Long", "86"),
            Map.entry("An Giang", "91"),
            Map.entry("Cần Thơ", "92"),
            Map.entry("Cà Mau", "96")
    );

    @Override
    @Transactional
    public void run(String... args) {
        if (provinceRepository.count() > 0) {
            log.info("🗺️ [INIT PROVINCES] Dữ liệu tỉnh thành đã có sẵn ({} tỉnh/thành). Bỏ qua seeding.", provinceRepository.count());
            return;
        }

        try {
            log.info("🗺️ [INIT PROVINCES] Bắt đầu nạp dữ liệu 34 tỉnh thành từ GeoJSON vào PostgreSQL/PostGIS...");
            seedProvinces();
        } catch (Exception e) {
            log.error("❌ [INIT PROVINCES ERROR] Lỗi khi nạp dữ liệu tỉnh thành: {}", e.getMessage(), e);
        }
    }

    private void seedProvinces() throws Exception {
        // 1. Đọc file nhãn tọa độ trung tâm (vn-province-labels.geojson)
        Map<String, LabelMeta> labelMap = new HashMap<>();
        ClassPathResource labelsResource = new ClassPathResource("geo/vn-province-labels.geojson");
        if (labelsResource.exists()) {
            try (InputStream is = labelsResource.getInputStream()) {
                JsonNode labelsRoot = objectMapper.readTree(is);
                JsonNode features = labelsRoot.path("features");
                for (JsonNode f : features) {
                    String name = f.path("properties").path("name").asText().trim();
                    String nameEn = f.path("properties").path("name_en").asText("").trim();
                    JsonNode coords = f.path("geometry").path("coordinates");
                    if (coords.isArray() && coords.size() >= 2) {
                        double lng = coords.get(0).asDouble();
                        double lat = coords.get(1).asDouble();
                        labelMap.put(name, new LabelMeta(nameEn, lat, lng));
                    }
                }
            }
        }

        // 2. Đọc file ranh giới polygon (vn-provinces.geojson)
        ClassPathResource provResource = new ClassPathResource("geo/vn-provinces.geojson");
        if (!provResource.exists()) {
            log.warn("⚠️ [INIT PROVINCES] Không tìm thấy file geo/vn-provinces.geojson trong resources");
            return;
        }

        List<Province> provincesToSave = new ArrayList<>();
        try (InputStream is = provResource.getInputStream()) {
            JsonNode provRoot = objectMapper.readTree(is);
            JsonNode features = provRoot.path("features");

            for (JsonNode f : features) {
                JsonNode props = f.path("properties");
                String shapeName = props.path("shapeName").asText("").trim();
                if (shapeName.isEmpty()) {
                    shapeName = props.path("name").asText("").trim();
                }

                String code = PROVINCE_CODE_MAP.get(shapeName);
                if (code == null) {
                    log.warn("⚠️ Không xác định được mã cho tỉnh: '{}'", shapeName);
                    continue;
                }

                LabelMeta labelMeta = labelMap.get(shapeName);
                String nameEn = labelMeta != null && !labelMeta.nameEn().isEmpty()
                        ? labelMeta.nameEn()
                        : props.path("name_en").asText("").trim();

                Double centerLat = labelMeta != null ? labelMeta.lat() : null;
                Double centerLng = labelMeta != null ? labelMeta.lng() : null;

                Geometry geom = parseGeoJsonGeometry(f.path("geometry"));
                if (geom != null) {
                    geom.setSRID(4326);
                }

                Province province = Province.builder()
                        .code(code)
                        .name(shapeName)
                        .nameEn(nameEn)
                        .centerLat(centerLat)
                        .centerLng(centerLng)
                        .geometry(geom)
                        .build();

                provincesToSave.add(province);
            }
        }

        provinceRepository.saveAll(provincesToSave);
        log.info("✅ [INIT PROVINCES] Đã nạp thành công {} tỉnh thành vào cơ sở dữ liệu!", provincesToSave.size());
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

    private record LabelMeta(String nameEn, Double lat, Double lng) {}
}
