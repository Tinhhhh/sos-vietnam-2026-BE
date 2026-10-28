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

    /** Kết quả gửi link qua SMS tới số của người phụ trách. */
    @JsonProperty("sms")
    private Sms sms;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sms {
        /** Cổng SMS đã nhận tin (điện thoại gửi đi sau vài giây). */
        @JsonProperty("sent")
        private boolean sent;

        /** "091****678" */
        @JsonProperty("phone_masked")
        private String phoneMasked;

        /** Lý do không gửi: chưa có số, chưa cấu hình cổng SMS, cổng lỗi... */
        @JsonProperty("error")
        private String error;
    }
}
