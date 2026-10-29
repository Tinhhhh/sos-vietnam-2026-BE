package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.UUID;

/**
 * Body của POST /api/v1/incidents/{id}/release = giao ca.
 * to_member_id: cán bộ nhận (unit_members, cùng đơn vị, đang rảnh); trống = 400 (ca đã nhận không trả về hàng đợi chung).
 * note: lý do giao ca (bắt buộc), lưu vào incident_claims.note của lượt giữ ca vừa kết thúc.
 */
@Data
public class ReleaseIncidentRequest {
    @JsonProperty("to_member_id")
    private UUID toMemberId;

    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    @JsonProperty("note")
    private String note;
}
