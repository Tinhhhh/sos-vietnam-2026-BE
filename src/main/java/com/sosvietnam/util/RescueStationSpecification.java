package com.sosvietnam.util;

import jakarta.persistence.criteria.JoinType;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.StationLevel;
import lombok.experimental.UtilityClass;
import org.springframework.data.jpa.domain.Specification;

/** Bộ lọc cho GET /api/v1/stations; tham số trống/null = không lọc. */
@UtilityClass
public class RescueStationSpecification {

    /**
     * Tên tỉnh chứa chuỗi tìm, không phân biệt hoa thường và dấu (hàm unaccent của Postgres, bật trong schema.sql):
     * "c" khớp "Cần Thơ", "Cà Mau"...; "can tho" khớp "Cần Thơ"; "da nang" khớp "Đà Nẵng".
     */
    public Specification<RescueStation> provinceContains(String province) {
        return (root, query, cb) -> {
            if (province == null || province.trim().isEmpty()) {
                return null;
            }
            String likePattern = "%" + escapeLike(province.trim().toLowerCase()) + "%";
            // province là khoá ngoại tới provinces: so theo tên tỉnh
            return cb.like(
                    cb.function("unaccent", String.class, cb.lower(root.join("province", JoinType.LEFT).get("name"))),
                    cb.function("unaccent", String.class, cb.literal(likePattern)),
                    '\\');
        };
    }

    public Specification<RescueStation> hasAgencyType(AgencyType agencyType) {
        return (root, query, cb) -> agencyType == null ? null : cb.equal(root.get("agencyType"), agencyType);
    }

    public Specification<RescueStation> hasLevel(StationLevel level) {
        return (root, query, cb) -> level == null ? null : cb.equal(root.get("level"), level);
    }

    // "%" và "_" người dùng gõ vào được tìm đúng ký tự, không thành ký tự đại diện
    private String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
