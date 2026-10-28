package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Size;
import lombok.*;

/**
 * Body tuỳ chọn của POST .../dispatch-plan/arrive. reason chỉ dùng khi người giữ ca xác nhận đã đến mà GPS chưa
 * trong bán kính / không có GPS (>= 5 ký tự); không có reason thì BE kiểm GPS.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ArriveRequest {

    @Size(max = 255, message = "Lý do tối đa 255 ký tự")
    @JsonProperty("reason")
    private String reason;
}
