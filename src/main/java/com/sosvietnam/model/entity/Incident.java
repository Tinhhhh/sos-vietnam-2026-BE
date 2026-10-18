package com.sosvietnam.model.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.CancelReason;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import jakarta.persistence.*;
import jakarta.validation.constraints.Pattern;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "incidents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident {

    @Id
    @Column(length = 50)
    private String id; // e.g. SOS-2026-XXXX

    @Column(name = "citizen_name", length = 150)
    private String citizenName;

    @Column(length = 10)
    @Pattern(regexp = "^0[0-9]{9}$", message = "Phone number must be 10 digits starting with 0.")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "emergency_type", nullable = false, length = 30)
    private EmergencyType emergencyType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private IncidentStatus status;

    @Column(columnDefinition = "TEXT")
    private String description;

    // Quick-pick labels chosen in the citizen form, e.g. "🔥 Cháy nhà dân / Chung
    // cư / Nhà xưởng"
    @Builder.Default
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "incident_tags", columnDefinition = "jsonb")
    private List<String> incidentTags = new ArrayList<>();

    @Column(columnDefinition = "TEXT")
    private String address;

    // Địa bàn theo toạ độ lúc báo tin (null = ngoài ranh giới dữ liệu / chưa xác định được xã)
    @ManyToOne
    @JoinColumn(name = "province_id")
    private Province province;

    @ManyToOne
    @JoinColumn(name = "ward_id")
    private Ward ward;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    // GPS accuracy reported by the citizen's device, in meters
    @Column(name = "accuracy")
    private Integer accuracy;

    // DB tự sinh từ latitude/longitude (generated column), dùng cho truy vấn không gian. Chỉ đọc.
    @JsonIgnore
    @Column(columnDefinition = "geometry(Point, 4326)", insertable = false, updatable = false)
    private Point location;

    // SHA-256 of the access token returned once to the citizen who reported the
    // incident
    @JsonIgnore
    @Column(name = "citizen_token_hash", length = 64)
    private String citizenTokenHash;

    // Mã tra cứu công khai (8 ký tự, util/LookupCodes): cấp lúc tạo ca, chỉ đưa cho dân khi ca hoàn tất
    @JsonIgnore
    @Column(name = "lookup_code", length = 8, nullable = false, unique = true)
    private String lookupCode;

    // ---- Phân luồng (module 5) ----
    // Lực lượng đang giữ ca (từ loại sự cố: TRAFFIC -> CSGT...)
    @Enumerated(EnumType.STRING)
    @Column(name = "routed_agency", length = 30)
    private AgencyType routedAgency;

    // Cấp đang giữ ca: WARD (Công an xã), PROVINCE (cấp tỉnh/phòng), NATIONAL (không xác định được tỉnh)
    @Enumerated(EnumType.STRING)
    @Column(name = "routed_level", length = 20)
    private UnitLevel routedLevel;

    // Hạn chót leo thang (tạo ca + 15 phút); null = ca không leo thang
    @Column(name = "escalate_at")
    private LocalDateTime escalateAt;

    // Lúc ca thực sự bị chuyển lên tỉnh; null = chưa leo thang
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;

    // ---- Nhận ca / khoá ca ----
    // Trực ban đang giữ ca; null = chưa ai nhận. Lịch sử từng lượt giữ ở bảng incident_claims.
    @Column(name = "handler_id")
    private UUID handlerId;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    // Lần nhận ca đầu tiên (thời gian nhận ca trên dashboard), không đổi khi nhả / giao lại
    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    // ---- Huỷ ca ----
    // Người dân tự huỷ, hoặc hệ thống huỷ ca chờ quá lâu (status = CANCELLED)
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "cancel_reason", length = 30)
    private CancelReason cancelReason;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        if (createdAt == null)
            createdAt = LocalDateTime.now();
        if (status == null)
            status = IncidentStatus.PENDING;
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // ---- Tên / mã địa bàn (null-safe, dùng cho DTO và luật quyền xem) ----
    public String provinceId() {
        return province == null ? null : province.getCode();
    }

    public String provinceName() {
        return province == null ? null : province.getName();
    }

    public Long wardId() {
        return ward == null ? null : ward.getId();
    }

    public String wardName() {
        return ward == null ? null : ward.getName();
    }
}
