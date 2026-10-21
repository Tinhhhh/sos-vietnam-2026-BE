package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.IncidentDocument;
import com.sosvietnam.model.payload.enums.CitizenSignChannel;
import com.sosvietnam.model.payload.enums.SignatureType;
import com.sosvietnam.model.payload.request.IncidentDocumentRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.function.Function;
import java.util.UUID;

/**
 * Văn bản báo cáo của ca (GET /incidents/{id}/document và các API ký).
 * saved = false: chưa lưu lần nào, fields là bản nháp BE tự điền từ dữ liệu ca.
 * editable = người đang xem sửa được nội dung (cán bộ, chưa đủ chữ ký).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDocumentResponse {

    @JsonProperty("incident_id")
    private String incidentId;

    @JsonProperty("doc_number")
    private String docNumber;

    @JsonProperty("saved")
    private boolean saved;

    /** Đủ chữ ký 2 bên: có hiệu lực, không sửa nữa */
    @JsonProperty("locked")
    private boolean locked;

    @JsonProperty("editable")
    private boolean editable;

    @JsonProperty("fields")
    private IncidentDocumentRequest fields;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("officer_signature")
    private Signature officerSignature;

    @JsonProperty("citizen_signature")
    private Signature citizenSignature;

    @JsonProperty("created_by_name")
    private String createdByName;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("updated_by_name")
    private String updatedByName;

    @JsonProperty("updated_at")
    private OffsetDateTime updatedAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Signature {
        @JsonProperty("name")
        private String name;

        @JsonProperty("type")
        private SignatureType type;

        /** data URL PNG khi vẽ tay */
        @JsonProperty("image")
        private String image;

        @JsonProperty("signed_at")
        private OffsetDateTime signedAt;

        /** Cán bộ đặt chữ ký (ô cán bộ: người ký; ô người dân: cán bộ ký hộ, null khi dân tự ký) */
        @JsonProperty("signed_by_name")
        private String signedByName;

        /** Chỉ ô người dân */
        @JsonProperty("channel")
        private CitizenSignChannel channel;

        @JsonProperty("channel_label")
        private String channelLabel;

        @JsonProperty("proxy_reason")
        private String proxyReason;
    }

    public static IncidentDocumentRequest fieldsOf(IncidentDocument d) {
        return IncidentDocumentRequest.builder()
                .headerUpper(d.getHeaderUpper())
                .headerLower(d.getHeaderLower())
                .placeDate(d.getPlaceDate())
                .officerName(d.getOfficerName())
                .officerTitle(d.getOfficerTitle())
                .unitName(d.getUnitName())
                .unitAddress(d.getUnitAddress())
                .unitPhone(d.getUnitPhone())
                .reporterName(d.getReporterName())
                .reporterPhone(d.getReporterPhone())
                .targetType(d.getTargetType())
                .incidentTime(d.getIncidentTime())
                .incidentAddress(d.getIncidentAddress())
                .coordinates(d.getCoordinates())
                .description(d.getDescription())
                .affectsCitizens(d.getAffectsCitizens())
                .needsHelp(d.getNeedsHelp())
                .helpRequest(d.getHelpRequest())
                .adviceGiven(d.getAdviceGiven())
                .resolutionResult(d.getResolutionResult())
                .build();
    }

    /**
     * @param saved      văn bản đã có trong DB (false = bản nháp tự điền)
     * @param nameOf     tra họ tên cán bộ theo account id (null-safe)
     */
    public static IncidentDocumentResponse from(IncidentDocument d, boolean saved, boolean editable,
                                                Double latitude, Double longitude, Function<UUID, String> nameOf) {
        return IncidentDocumentResponse.builder()
                .incidentId(d.getIncidentId())
                .docNumber(d.getDocNumber())
                .saved(saved)
                .locked(d.locked())
                .editable(editable && !d.locked())
                .fields(fieldsOf(d))
                .latitude(latitude)
                .longitude(longitude)
                .officerSignature(!d.officerSigned() ? null : Signature.builder()
                        .name(d.getOfficerSignName())
                        .type(d.getOfficerSignType())
                        .image(d.getOfficerSignature())
                        .signedAt(withZone(d.getOfficerSignedAt()))
                        .signedByName(nameOf.apply(d.getOfficerSignedBy()))
                        .build())
                .citizenSignature(!d.citizenSigned() ? null : Signature.builder()
                        .name(d.getCitizenSignName())
                        .type(d.getCitizenSignType())
                        .image(d.getCitizenSignature())
                        .signedAt(withZone(d.getCitizenSignedAt()))
                        .signedByName(nameOf.apply(d.getCitizenProxyBy()))
                        .channel(d.getCitizenSignChannel())
                        .channelLabel(d.getCitizenSignChannel() == null ? null : d.getCitizenSignChannel().getLabel())
                        .proxyReason(d.getCitizenProxyReason())
                        .build())
                .createdByName(saved ? nameOf.apply(d.getCreatedBy()) : null)
                .createdAt(saved ? withZone(d.getCreatedAt()) : null)
                .updatedByName(saved ? nameOf.apply(d.getUpdatedBy()) : null)
                .updatedAt(saved ? withZone(d.getUpdatedAt()) : null)
                .build();
    }

    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
