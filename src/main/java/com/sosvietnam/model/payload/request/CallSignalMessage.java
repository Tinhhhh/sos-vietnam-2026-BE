package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một tín hiệu gọi qua STOMP (SEND /app/calls, nhận ở /user/queue/calls).
 * sdp / candidate là dữ liệu WebRTC của trình duyệt, server chỉ chuyển tiếp nguyên vẹn, không lưu.
 * from / peer_name / unit_name do server điền khi chuyển tiếp (client gửi lên sẽ bị ghi đè).
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CallSignalMessage {

    @JsonProperty("incident_id")
    private String incidentId;

    @JsonProperty("call_id")
    private String callId;

    /** ring | accept | reject | offer | answer | ice | quality | end; server gửi thêm dismiss (đã nghe / từ chối ở máy khác) */
    @JsonProperty("action")
    private String action;

    @JsonProperty("sdp")
    private JsonNode sdp;

    @JsonProperty("candidate")
    private JsonNode candidate;

    /** detail | smooth (action = quality) */
    @JsonProperty("quality")
    private String quality;

    /** Lý do kết thúc / từ chối (network, busy, offline, not_handler, no_field, no_delegate, closed...) */
    @JsonProperty("reason")
    private String reason;

    /**
     * Bên nhận: officer (cán bộ giữ ca) | citizen (người báo tin) | field (người phụ trách hiện trường)
     * | delegate (cán bộ xã được uỷ quyền). Bỏ trống thì như cũ: cán bộ -> dân, dân -> cán bộ, người phụ trách -> cán bộ.
     */
    @JsonProperty("to")
    private String to;

    /** Bên gửi, server điền: officer | citizen | field | delegate | server */
    @JsonProperty("from")
    private String from;

    @JsonProperty("peer_name")
    private String peerName;

    @JsonProperty("unit_name")
    private String unitName;
}
