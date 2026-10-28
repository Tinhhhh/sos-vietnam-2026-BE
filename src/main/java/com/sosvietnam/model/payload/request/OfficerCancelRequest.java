package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

/** Body của POST /api/v1/incidents/{id}/officer-cancel: cán bộ giữ ca huỷ ca, bắt buộc lý do. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfficerCancelRequest {

    @NotBlank(message = "Vui lòng nhập lý do huỷ ca")
    @Size(min = 5, max = 255, message = "Lý do từ 5 đến 255 ký tự")
    @JsonProperty("reason")
    private String reason;

    /** true = cán bộ kết luận báo khống (CancelReason.FALSE_ALARM, tính vào khoá SĐT). */
    @JsonProperty("false_alarm")
    private boolean falseAlarm;
}
