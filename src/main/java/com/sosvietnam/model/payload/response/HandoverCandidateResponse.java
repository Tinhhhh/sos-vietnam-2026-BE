package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/** Trực ban cùng đơn vị có thể nhận bàn giao ca. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class HandoverCandidateResponse {
    @JsonProperty("account_id")
    private UUID accountId;

    @JsonProperty("full_name")
    private String fullName;

    // Đang kết nối màn hình trực ban (STOMP)
    @JsonProperty("online")
    private boolean online;

    // Đang giữ một ca khác: không giao được (mỗi người chỉ giữ 1 ca)
    @JsonProperty("busy")
    private boolean busy;
}
