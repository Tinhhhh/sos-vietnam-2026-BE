package com.sosvietnam.repository;

import com.sosvietnam.model.payload.enums.PoliceRank;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Liên hệ của trạm lấy từ tài khoản đơn vị cùng lực lượng / cấp / địa bàn (cấp xã: cùng xã, cấp tỉnh: cùng tỉnh):
 * chỉ huy (COMMANDER đang hoạt động, sớm nhất), SĐT trực ban hiện tại, email đơn vị.
 * Trạm doanh nghiệp / toàn quốc / y tế không có đơn vị tương ứng thì không có dòng.
 */
@Repository
@RequiredArgsConstructor
public class StationContactRepository {

    public record Contact(String officer, String dutyPhone, String email) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public Map<Long, Contact> findByStationIds(Collection<Long> stationIds) {
        Map<Long, Contact> result = new HashMap<>();
        if (stationIds.isEmpty()) return result;
        jdbc.query("""
                SELECT s.id, cm.rank, cm.full_name, dm.phone, a.email
                FROM rescue_stations s
                JOIN accounts a ON a.unit_account AND a.is_active AND a.agency_type = s.agency_type
                     AND a.province_id = s.province_id AND a.unit_level = s.level
                     AND (s.level = 'PROVINCE' OR a.ward_id = s.ward_id)
                LEFT JOIN LATERAL (
                    SELECT m.rank, m.full_name FROM unit_members m
                    WHERE m.account_id = a.account_id AND m.active AND m.unit_role = 'COMMANDER'
                    ORDER BY m.created_at LIMIT 1) cm ON true
                LEFT JOIN LATERAL (
                    SELECT m.phone FROM unit_duty_assignments d JOIN unit_members m ON m.member_id = d.member_id
                    WHERE d.account_id = a.account_id AND d.ended_at IS NULL) dm ON true
                WHERE s.id IN (:ids)
                """, new MapSqlParameterSource("ids", stationIds), rs -> {
            result.put(rs.getLong(1), new Contact(officerName(rs.getString(2), rs.getString(3)), rs.getString(4), rs.getString(5)));
        });
        return result;
    }

    /** "Trung tá Vũ Công Hùng" (cấp bậc lạ thì bỏ). */
    static String officerName(String rank, String fullName) {
        if (fullName == null) return null;
        try {
            return rank == null ? fullName : PoliceRank.valueOf(rank).getLabel() + " " + fullName;
        } catch (IllegalArgumentException e) {
            return fullName;
        }
    }
}
