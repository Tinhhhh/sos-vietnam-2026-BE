package com.sosvietnam.model.entity;

import com.sosvietnam.model.payload.enums.CitizenSignChannel;
import com.sosvietnam.model.payload.enums.SignatureType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Văn bản báo cáo của một ca đã hoàn tất: "Phiếu thông báo và tiếp nhận thông tin ứng phó sự cố khẩn cấp"
 * (mẫu bản gốc). Sửa được tới khi đủ chữ ký 2 bên (cán bộ + người dân / cán bộ ký hộ).
 */
@Entity
@Table(name = "incident_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class IncidentDocument {

    @Id
    @Column(name = "incident_id", length = 50)
    private String incidentId;

    @Column(name = "doc_number", length = 80, nullable = false)
    private String docNumber;

    @Column(name = "header_upper", length = 200)
    private String headerUpper;

    @Column(name = "header_lower", length = 200)
    private String headerLower;

    @Column(name = "place_date", length = 200)
    private String placeDate;

    @Column(name = "officer_name", length = 200)
    private String officerName;

    @Column(name = "officer_title", length = 200)
    private String officerTitle;

    @Column(name = "unit_name", length = 300)
    private String unitName;

    @Column(name = "unit_address", length = 500)
    private String unitAddress;

    @Column(name = "unit_phone", length = 100)
    private String unitPhone;

    @Column(name = "reporter_name", length = 200)
    private String reporterName;

    @Column(name = "reporter_phone", length = 30)
    private String reporterPhone;

    @Column(name = "target_type", length = 50)
    private String targetType;

    @Column(name = "incident_time", length = 100)
    private String incidentTime;

    @Column(name = "incident_address", length = 500)
    private String incidentAddress;

    @Column(name = "coordinates", length = 100)
    private String coordinates;

    @Column(name = "description", length = 3000)
    private String description;

    @Column(name = "affects_citizens")
    private Boolean affectsCitizens;

    @Column(name = "needs_help")
    private Boolean needsHelp;

    @Column(name = "help_request", length = 2000)
    private String helpRequest;

    @Column(name = "advice_given", length = 2000)
    private String adviceGiven;

    @Column(name = "resolution_result", length = 3000)
    private String resolutionResult;

    // ---- Chữ ký cán bộ ----
    @Column(name = "officer_sign_name", length = 200)
    private String officerSignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "officer_sign_type", length = 10)
    private SignatureType officerSignType;

    /** Ảnh chữ ký vẽ tay (data URL PNG), null khi gõ họ tên */
    @Column(name = "officer_signature", columnDefinition = "text")
    private String officerSignature;

    @Column(name = "officer_signed_by")
    private UUID officerSignedBy;

    @Column(name = "officer_signed_at")
    private LocalDateTime officerSignedAt;

    // ---- Chữ ký người dân ----
    @Column(name = "citizen_sign_name", length = 200)
    private String citizenSignName;

    @Enumerated(EnumType.STRING)
    @Column(name = "citizen_sign_type", length = 10)
    private SignatureType citizenSignType;

    @Column(name = "citizen_signature", columnDefinition = "text")
    private String citizenSignature;

    @Column(name = "citizen_signed_at")
    private LocalDateTime citizenSignedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "citizen_sign_channel", length = 20)
    private CitizenSignChannel citizenSignChannel;

    /** Cán bộ ký hộ người dân (citizen_sign_channel = OFFICER_PROXY) */
    @Column(name = "citizen_proxy_by")
    private UUID citizenProxyBy;

    @Column(name = "citizen_proxy_reason", length = 500)
    private String citizenProxyReason;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_by")
    private UUID updatedBy;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public boolean officerSigned() {
        return officerSignedAt != null;
    }

    public boolean citizenSigned() {
        return citizenSignedAt != null;
    }

    /** Đủ chữ ký 2 bên: văn bản có hiệu lực, khoá sửa */
    public boolean locked() {
        return officerSigned() && citizenSigned();
    }

    /**
     * Tình trạng văn bản phía người dân: NONE (chưa lập), AWAITING_CITIZEN (đã lập, dân chưa ký),
     * CITIZEN_SIGNED (dân đã ký, chờ cán bộ), SIGNED (đủ 2 bên).
     */
    public static String statusOf(IncidentDocument d) {
        if (d == null) return "NONE";
        if (d.locked()) return "SIGNED";
        return d.citizenSigned() ? "CITIZEN_SIGNED" : "AWAITING_CITIZEN";
    }

    public void clearSignatures() {
        officerSignName = null;
        officerSignType = null;
        officerSignature = null;
        officerSignedBy = null;
        officerSignedAt = null;
        citizenSignName = null;
        citizenSignType = null;
        citizenSignature = null;
        citizenSignedAt = null;
        citizenSignChannel = null;
        citizenProxyBy = null;
        citizenProxyReason = null;
    }
}
