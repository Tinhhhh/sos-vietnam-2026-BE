package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.ClaimEndReason;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Một lượt giữ ca của một cán bộ (unit_members): nhận ca mở một dòng, nhả / giao lại / kết thúc ca đóng dòng đó.
 * Giao lại thì đóng dòng của người cũ (HANDED_OVER, handed_to = người mới) và mở dòng mới cho người nhận.
 * Dòng đang mở (released_at null) khớp với incidents.handler_id.
 */
@Entity
@Table(name = "incident_claims", indexes = {
        @Index(name = "ix_incident_claims_incident", columnList = "incident_id"),
        @Index(name = "ix_incident_claims_member", columnList = "member_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    private Incident incident;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private UnitMember member;

    @Column(name = "claimed_at", nullable = false)
    private LocalDateTime claimedAt;

    @Column(name = "released_at")
    private LocalDateTime releasedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_reason", length = 20)
    private ClaimEndReason endReason;

    // Người được giao lại (khi end_reason = HANDED_OVER)
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handed_to_id")
    private UnitMember handedTo;

    @Column(name = "note", length = 255)
    private String note;
}
