package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.PoliceRank;
import com.sosvietnam.model.payload.enums.UnitRole;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Một cán bộ của đơn vị (người thật), đăng nhập bằng tài khoản đơn vị rồi chọn mình + nhập PIN.
 * Mọi thao tác trên ca (giữ ca, điều động, ký văn bản...) ghi theo cán bộ. Admin có 1 cán bộ ẩn, không có PIN.
 * Tài khoản có trước V11 sinh cán bộ có cùng id (member_id = account_id).
 */
@Entity
@Table(name = "unit_members")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnitMember {

    @Id
    @Column(name = "member_id")
    private UUID id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Column(name = "full_name", nullable = false, length = 150)
    private String fullName;

    @Column(name = "badge_number", length = 30)
    private String badgeNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "rank", length = 30)
    private PoliceRank rank;

    @Column(name = "position", length = 150)
    private String position;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_role", nullable = false, length = 20)
    private UnitRole unitRole;

    @Column(name = "phone", length = 10)
    private String phone;

    @JsonIgnore
    @Column(name = "pin_hash")
    private String pinHash;

    @JsonIgnore
    @Column(name = "activation_code_hash")
    private String activationCodeHash;

    @Column(name = "activation_expires_at")
    private LocalDateTime activationExpiresAt;

    @Builder.Default
    @Column(name = "failed_pin_count", nullable = false)
    private int failedPinCount = 0;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = LocalDateTime.now();
    }

    public boolean isActivated() {
        return pinHash != null;
    }

    public boolean isLocked() {
        return lockedAt != null;
    }

    /** "Đại uý Nguyễn Văn An" (có cấp bậc thì ghi trước tên). */
    public String displayName() {
        return rank == null ? fullName : rank.getLabel() + " " + fullName;
    }
}
