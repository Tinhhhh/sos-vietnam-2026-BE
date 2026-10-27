package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.OffsetDateTime;

/** Link mới cho người phụ trách hiện trường: mã chỉ trả đúng 1 lần, FE ghép thành {origin}/f/{token}. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldLinkResponse {

    @JsonProperty("token")
    private String token;

    /** "/f/{token}" */
    @JsonProperty("path")
    private String path;

    @JsonProperty("issued_at")
    private OffsetDateTime issuedAt;

    /** Người cầm link (họ tên, số hiệu) để cán bộ biết gửi cho ai. */
    @JsonProperty("field_member")
    private DispatchPlanResponse.Officer fieldMember;
}
