package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.util.UUID;

/**
 * Body tuỳ chọn của POST .../dispatch-plan/depart ("Khởi hành"). member_id: chọn luôn người phụ trách hiện trường
 * (cách cũ, chọn + đi một lần); bỏ trống thì dùng người đã chọn trước (PUT .../field-member).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartRequest {

    @JsonProperty("member_id")
    private UUID memberId;
}
