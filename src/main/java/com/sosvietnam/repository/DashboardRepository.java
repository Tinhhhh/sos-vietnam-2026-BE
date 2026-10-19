package com.sosvietnam.repository;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Số liệu dashboard tính thẳng bằng SQL gộp nhóm trên bảng incidents (không tải ca về Java).
 * Mọi câu dùng chung điều kiện phạm vi (Filter): lực lượng, tỉnh, xã so khớp sau khi bỏ dấu / hoa thường /
 * tiền tố "TP.", giống IncidentAccess. Giờ trong DB là giờ của server (cột timestamp không múi giờ),
 * nên đổi sang giờ Việt Nam bằng :serverZone trước khi chia theo giờ / ngày.
 */
@Repository
@RequiredArgsConstructor
public class DashboardRepository {

    /** Phạm vi + khoảng thời gian [start, end) theo giờ server. Trường null = không lọc theo trường đó. */
    public record Filter(AgencyType agency, String provinceId, Long wardId, boolean wardOriginOnly,
                         LocalDateTime start, LocalDateTime end, String serverZone, long slaMinutes) {
    }

    private static final String VN_TIME =
            "((i.created_at AT TIME ZONE :serverZone) AT TIME ZONE 'Asia/Ho_Chi_Minh')";
    private static final String ACCEPT_SECONDS = "EXTRACT(EPOCH FROM (i.accepted_at - i.created_at))";
    private static final String WITHIN_SLA =
            "i.accepted_at IS NOT NULL AND i.accepted_at - i.created_at <= make_interval(mins => :slaMinutes)";

    private final NamedParameterJdbcTemplate jdbc;

    public Map<String, Object> kpis(Filter f) {
        String sql = "SELECT count(*) AS total, "
                + "count(*) FILTER (WHERE i.accepted_at IS NOT NULL) AS accepted, "
                + "count(*) FILTER (WHERE i.escalated_at IS NOT NULL) AS escalated, "
                + "avg(" + ACCEPT_SECONDS + ") FILTER (WHERE i.accepted_at IS NOT NULL) AS avg_accept, "
                + "percentile_cont(0.5) WITHIN GROUP (ORDER BY " + ACCEPT_SECONDS + ") "
                + "  FILTER (WHERE i.accepted_at IS NOT NULL) AS median_accept, "
                + "count(*) FILTER (WHERE " + WITHIN_SLA + ") AS within_sla "
                + "FROM incidents i WHERE " + where(f, true);
        return jdbc.queryForMap(sql, params(f));
    }

    /** Số ca của cùng phạm vi trong một khoảng khác (kỳ trước). */
    public long count(Filter f) {
        Long n = jdbc.queryForObject("SELECT count(*) FROM incidents i WHERE " + where(f, true), params(f), Long.class);
        return n == null ? 0 : n;
    }

    /** Ca đang chờ nhận ngay lúc này trong phạm vi (không giới hạn ngày tạo). */
    public long pendingNow(Filter f) {
        String sql = "SELECT count(*) FROM incidents i WHERE i.status = :pending AND " + where(f, false);
        Long n = jdbc.queryForObject(sql, params(f).addValue("pending", IncidentStatus.PENDING.name()), Long.class);
        return n == null ? 0 : n;
    }

    /** Nhãn sự cố phổ biến nhất (incident_tag); ca không chọn nhãn gom vào tag null. */
    public List<Map<String, Object>> byTag(Filter f, int limit) {
        String sql = "SELECT i.incident_tag AS tag, count(*) AS cnt FROM incidents i "
                + "WHERE " + where(f, true) + " GROUP BY i.incident_tag ORDER BY cnt DESC, i.incident_tag LIMIT :limit";
        return jdbc.queryForList(sql, params(f).addValue("limit", limit));
    }

    public List<Map<String, Object>> byHour(Filter f) {
        String sql = "SELECT EXTRACT(HOUR FROM " + VN_TIME + ")::int AS hour, count(*) AS cnt "
                + "FROM incidents i WHERE " + where(f, true) + " GROUP BY 1";
        return jdbc.queryForList(sql, params(f));
    }

    public List<Map<String, Object>> byDay(Filter f) {
        String sql = "SELECT CAST(" + VN_TIME + " AS date) AS day, count(*) AS cnt, "
                + "count(*) FILTER (WHERE i.escalated_at IS NOT NULL) AS escalated, "
                + "count(*) FILTER (WHERE i.accepted_at IS NOT NULL) AS accepted, "
                + "count(*) FILTER (WHERE " + WITHIN_SLA + ") AS within_sla "
                + "FROM incidents i WHERE " + where(f, true) + " GROUP BY 1";
        return jdbc.queryForList(sql, params(f));
    }

    /** isodow: 1 = Thứ 2 ... 7 = Chủ nhật. */
    public List<Map<String, Object>> heatmap(Filter f) {
        String sql = "SELECT EXTRACT(ISODOW FROM " + VN_TIME + ")::int AS dow, "
                + "EXTRACT(HOUR FROM " + VN_TIME + ")::int AS hour, count(*) AS cnt "
                + "FROM incidents i WHERE " + where(f, true) + " GROUP BY 1, 2";
        return jdbc.queryForList(sql, params(f));
    }

    /** Thống kê theo địa bàn: "ward" (các xã của 1 tỉnh) hoặc "province" (các tỉnh). Tên lấy qua khoá ngoại. */
    public List<Map<String, Object>> byArea(Filter f, String areaColumn) {
        String join;
        if ("ward".equals(areaColumn)) {
            join = "LEFT JOIN wards a ON a.id = i.ward_id ";
        } else if ("province".equals(areaColumn)) {
            join = "LEFT JOIN provinces a ON a.code = i.province_id ";
        } else {
            throw new IllegalArgumentException("areaColumn");
        }
        String sql = "SELECT a.name AS name, count(*) AS total, "
                + "count(*) FILTER (WHERE i.status = :pending) AS pending, "
                + "count(*) FILTER (WHERE i.escalated_at IS NOT NULL) AS escalated, "
                + "count(*) FILTER (WHERE i.accepted_at IS NOT NULL) AS accepted, "
                + "avg(" + ACCEPT_SECONDS + ") FILTER (WHERE i.accepted_at IS NOT NULL) AS avg_accept, "
                + "count(*) FILTER (WHERE " + WITHIN_SLA + ") AS within_sla "
                + "FROM incidents i " + join + "WHERE " + where(f, true) + " GROUP BY 1 ORDER BY escalated DESC, total DESC";
        return jdbc.queryForList(sql, params(f).addValue("pending", IncidentStatus.PENDING.name()));
    }

    public List<Map<String, Object>> byAgency(Filter f) {
        String sql = "SELECT i.routed_agency AS agency, count(*) AS cnt FROM incidents i "
                + "WHERE i.routed_agency IS NOT NULL AND " + where(f, true) + " GROUP BY 1 ORDER BY cnt DESC";
        return jdbc.queryForList(sql, params(f));
    }

    private static String where(Filter f, boolean withDates) {
        StringBuilder sb = new StringBuilder("1 = 1");
        if (withDates) sb.append(" AND i.created_at >= :start AND i.created_at < :end");
        if (f.agency() != null) sb.append(" AND i.routed_agency = :agency");
        if (f.provinceId() != null) sb.append(" AND i.province_id = :provinceId");
        if (f.wardId() != null) sb.append(" AND i.ward_id = :wardId");
        // Cấp xã chỉ tính ca xuất phát ở xã (có hạn leo thang), giống quyền xem ở hàng đợi
        if (f.wardOriginOnly()) sb.append(" AND i.escalate_at IS NOT NULL");
        return sb.toString();
    }

    private static MapSqlParameterSource params(Filter f) {
        return new MapSqlParameterSource()
                .addValue("start", Timestamp.valueOf(f.start()))
                .addValue("end", Timestamp.valueOf(f.end()))
                .addValue("agency", f.agency() == null ? null : f.agency().name())
                .addValue("provinceId", f.provinceId())
                .addValue("wardId", f.wardId())
                .addValue("serverZone", f.serverZone())
                .addValue("slaMinutes", (int) f.slaMinutes()); // make_interval(mins => int)
    }
}
