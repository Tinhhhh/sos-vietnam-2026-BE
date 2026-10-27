package com.sosvietnam.repository;

import com.sosvietnam.model.entity.PhoneLock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PhoneLockRepository extends JpaRepository<PhoneLock, Long> {

    Optional<PhoneLock> findFirstByPhoneOrderByLockedAtDesc(String phone);

    /** Lần khoá (chưa bị ADMIN gỡ) của SĐT từ mốc since: lần thứ 3 trong 30 ngày thì vĩnh viễn. */
    long countByPhoneAndLiftedAtIsNullAndLockedAtGreaterThanEqual(String phone, LocalDateTime since);

    /** Khoá đang hiệu lực (vĩnh viễn, hoặc chưa hết hạn), chưa bị gỡ. */
    @Query("""
        SELECT l FROM PhoneLock l
        WHERE l.liftedAt IS NULL AND (l.permanent = true OR l.lockedUntil > :now)
          AND (:phone IS NULL OR l.phone = :phone)
        ORDER BY l.lockedAt DESC
        """)
    Page<PhoneLock> findActive(@Param("phone") String phone, @Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
        SELECT l FROM PhoneLock l WHERE (:phone IS NULL OR l.phone = :phone) ORDER BY l.lockedAt DESC
        """)
    Page<PhoneLock> findAllByPhone(@Param("phone") String phone, Pageable pageable);

    /** Lần khoá đang hiệu lực của 1 SĐT (mới nhất trước). */
    @Query("""
        SELECT l FROM PhoneLock l
        WHERE l.phone = :phone AND l.liftedAt IS NULL AND (l.permanent = true OR l.lockedUntil > :now)
        ORDER BY l.lockedAt DESC
        """)
    List<PhoneLock> findActiveForPhone(@Param("phone") String phone, @Param("now") LocalDateTime now);

    /** Ca bị cán bộ kết luận báo khống từ mốc since: huỷ "Báo khống" hoặc kết thúc ca kết quả FALSE_ALARM. */
    @Query(value = """
        SELECT COUNT(*) FROM incidents i
        WHERE i.phone = :phone AND (
              (i.cancel_reason = 'FALSE_ALARM' AND i.cancelled_at >= :since)
           OR EXISTS (SELECT 1 FROM incident_reports r
                      WHERE r.incident_id = i.id AND r.result = 'FALSE_ALARM' AND r.created_at >= :since))
        """, nativeQuery = true)
    long countFalseAlarmsSince(@Param("phone") String phone, @Param("since") LocalDateTime since);

    /** Ca người dân tự huỷ vì "Bấm nhầm" từ mốc since. */
    @Query(value = """
        SELECT COUNT(*) FROM incidents i
        WHERE i.phone = :phone AND i.cancel_reason = 'MISTAKE' AND i.cancelled_at >= :since
        """, nativeQuery = true)
    long countMistakeCancelsSince(@Param("phone") String phone, @Param("since") LocalDateTime since);

    /** Số ca báo khống trong khoảng từ since theo từng IP (cờ cảnh báo trên thẻ, không dùng để chặn). */
    @Query(value = """
        SELECT i.client_ip AS ip, COUNT(*) AS n FROM incidents i
        WHERE i.client_ip IN (:ips) AND (
              (i.cancel_reason = 'FALSE_ALARM' AND i.cancelled_at >= :since)
           OR EXISTS (SELECT 1 FROM incident_reports r
                      WHERE r.incident_id = i.id AND r.result = 'FALSE_ALARM' AND r.created_at >= :since))
        GROUP BY i.client_ip
        """, nativeQuery = true)
    List<Object[]> countFalseAlarmsByIp(@Param("ips") Collection<String> ips, @Param("since") LocalDateTime since);
}
