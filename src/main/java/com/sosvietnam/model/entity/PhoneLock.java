package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.PhoneLockReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một lần khoá SĐT gửi SOS (V20). Khoá thường 24 giờ; lần khoá thứ 3 trong 30 ngày thì vĩnh viễn
 * (locked_until null), chỉ ADMIN gỡ (người dân lên công an trình bày hoặc liên hệ hệ thống).
 */
@Entity
@Table(name = "phone_locks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PhoneLock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 10)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PhoneLockReason reason;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    // null = vĩnh viễn
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(nullable = false)
    private boolean permanent;

    // ADMIN gỡ tay; hết hạn tự nhiên thì để trống
    @Column(name = "lifted_at")
    private LocalDateTime liftedAt;

    @Column(name = "lifted_by")
    private UUID liftedBy;

    @Column(name = "lift_note", length = 255)
    private String liftNote;

    /** Đang có hiệu lực lúc now. */
    public boolean activeAt(LocalDateTime now) {
        return liftedAt == null && (permanent || (lockedUntil != null && lockedUntil.isAfter(now)));
    }
}
