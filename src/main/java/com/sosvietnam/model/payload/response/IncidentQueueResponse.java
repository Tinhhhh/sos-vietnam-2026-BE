package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * Một ca trong hàng đợi của trực ban. can_handle / held_by_me tính theo người xem nên mỗi người nhận
 * bản riêng. Thời gian có kèm múi giờ để đồng hồ đếm ngược ở FE đúng dù server chạy UTC.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentQueueResponse {

    @JsonProperty("id")
    private String id;

    @JsonProperty("emergency_type")
    private EmergencyType emergencyType;

    @JsonProperty("status")
    private IncidentStatus status;

    @JsonProperty("routed_agency")
    private AgencyType routedAgency;

    @JsonProperty("routed_level")
    private UnitLevel routedLevel;

    @JsonProperty("province")
    private String province;

    @JsonProperty("ward")
    private String ward;

    @JsonProperty("address")
    private String address;

    @JsonProperty("latitude")
    private Double latitude;

    @JsonProperty("longitude")
    private Double longitude;

    @JsonProperty("incident_tags")
    private List<String> incidentTags;

    @JsonProperty("description")
    private String description;

    @JsonProperty("citizen_name")
    private String citizenName;

    @JsonProperty("phone")
    private String phone;

    @JsonProperty("media_count")
    private int mediaCount;

    /** Ảnh hiện trường (thẻ ca hiện biểu tượng ảnh + số lượng). */
    @JsonProperty("image_count")
    private int imageCount;

    /** Video hiện trường (biểu tượng máy quay). */
    @JsonProperty("video_count")
    private int videoCount;

    @JsonProperty("created_at")
    private OffsetDateTime createdAt;

    @JsonProperty("escalate_at")
    private OffsetDateTime escalateAt;

    @JsonProperty("escalated_at")
    private OffsetDateTime escalatedAt;

    /** Vì sao chuyển lên tỉnh: NO_RESPONSE_TIMEOUT / CITIZEN_REQUEST / OFFICER_REQUEST (V9) */
    /** Lực lượng trước khi chuyển + lý do (V10), null nếu ca chưa chuyển lực lượng */
    @JsonProperty("transferred_from")
    private com.sosvietnam.model.payload.enums.AgencyType transferredFrom;

    @JsonProperty("transferred_from_label")
    private String transferredFromLabel;

    @JsonProperty("transfer_reason")
    private String transferReason;

    @JsonProperty("escalation_reason")
    private com.sosvietnam.model.payload.enums.EscalationReason escalationReason;

    @JsonProperty("escalation_reason_label")
    private String escalationReasonLabel;

    @JsonProperty("report_type")
    private com.sosvietnam.model.payload.enums.ReportType reportType;

    @JsonProperty("report_type_label")
    private String reportTypeLabel;

    /** Lý do cán bộ xã ghi khi tự chuyển lên tỉnh */
    @JsonProperty("escalation_note")
    private String escalationNote;

    // Người đang giữ ca; null = chưa ai nhận
    @JsonProperty("handler")
    private Handler handler;

    // Ca do chính người xem đang giữ
    @JsonProperty("held_by_me")
    private boolean heldByMe;

    // Người xem đúng cấp đang giữ ca (được nhận / thao tác); false = chỉ theo dõi
    @JsonProperty("can_handle")
    private boolean canHandle;

    /** Người phụ trách hiện trường chọn lúc "Yêu cầu điều động" (bước chọn trạm dùng lại khi đơn vị mình đi). */
    @JsonProperty("field_member_id")
    private java.util.UUID fieldMemberId;

    /** Bước điều động hiện tại 1-4 của lượt chủ trì (null = chưa chọn trạm / đã bị từ chối). */
    @JsonProperty("dispatch_step")
    private Integer dispatchStep;

    /** Bước 2 uỷ quyền: ca được giao cho một đơn vị cấp xã (null nếu không có). */
    @JsonProperty("delegation")
    private Delegation delegation;

    /** Ca khác đang mở của cùng số điện thoại (tối đa 2 ca / số): cán bộ biết để tránh xử lý trùng. */
    @JsonProperty("other_open_cases")
    private List<OpenCase> otherOpenCases;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OpenCase {
        @JsonProperty("id")
        private String id;
        @JsonProperty("type_label")
        private String typeLabel;
        @JsonProperty("status_label")
        private String statusLabel;

        public static OpenCase of(Incident i) {
            return new OpenCase(i.getId(), i.getEmergencyType() == null ? null : i.getEmergencyType().getLabel(),
                    i.getStatus() == null ? null : i.getStatus().getLabel());
        }
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Delegation {
        @JsonProperty("dispatch_id")
        private Long dispatchId;
        @JsonProperty("status")
        private com.sosvietnam.model.payload.enums.DispatchStatus status;
        @JsonProperty("status_label")
        private String statusLabel;
        @JsonProperty("step")
        private int step;
        @JsonProperty("station_name")
        private String stationName;
        // Đơn vị giao việc (vd "Công an · Cấp tỉnh")
        @JsonProperty("from_unit")
        private String fromUnit;
        @JsonProperty("from_officer")
        private String fromOfficer;
        @JsonProperty("assigned_to")
        private Handler assignedTo;
        // Người xem là cán bộ được giao (được nhận / từ chối / xuất phát)
        @JsonProperty("assigned_to_me")
        private boolean assignedToMe;
        // Người xem thuộc đơn vị được uỷ quyền (thấy ca vì được uỷ quyền, không phải vì phân luồng)
        @JsonProperty("delegated_to_my_unit")
        private boolean delegatedToMyUnit;
        @JsonProperty("reject_reason")
        private String rejectReason;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Handler {
        @JsonProperty("member_id")
        private UUID memberId;
        @JsonProperty("full_name")
        private String fullName;
    }

    /** Số file đính kèm của một ca: tổng và theo loại. */
    public record MediaCounts(int total, int images, int videos) {
        public static final MediaCounts NONE = new MediaCounts(0, 0, 0);
    }

    public static IncidentQueueResponse from(Incident i, Handler handler, boolean heldByMe, boolean canHandle,
                                             MediaCounts media) {
        return IncidentQueueResponse.builder()
                .id(i.getId())
                .emergencyType(i.getEmergencyType())
                .status(i.getStatus())
                .routedAgency(i.getRoutedAgency())
                .routedLevel(i.getRoutedLevel())
                .fieldMemberId(i.getFieldMemberId())
                .province(i.provinceName())
                .ward(i.wardName())
                .address(i.getAddress())
                .latitude(i.getLatitude())
                .longitude(i.getLongitude())
                .incidentTags(i.tags())
                .description(i.getDescription())
                .citizenName(i.getCitizenName())
                .phone(i.getPhone())
                .mediaCount(media.total())
                .imageCount(media.images())
                .videoCount(media.videos())
                .createdAt(withZone(i.getCreatedAt()))
                .escalateAt(withZone(i.getEscalateAt()))
                .escalatedAt(withZone(i.getEscalatedAt()))
                .transferredFrom(i.getTransferredFrom())
                .transferredFromLabel(i.getTransferredFrom() == null ? null : i.getTransferredFrom().getLabel())
                .transferReason(i.getTransferReason())
                .escalationReason(i.getEscalationReason())
                .escalationReasonLabel(i.getEscalationReason() == null ? null : i.getEscalationReason().getLabel())
                .reportType(i.getReportType())
                .reportTypeLabel(i.getReportType() == null ? null : i.getReportType().getLabel())
                .escalationNote(i.getEscalationNote())
                .handler(handler)
                .heldByMe(heldByMe)
                .canHandle(canHandle)
                .build();
    }

    // LocalDateTime trong DB là giờ của server (LocalDateTime.now())
    private static OffsetDateTime withZone(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
