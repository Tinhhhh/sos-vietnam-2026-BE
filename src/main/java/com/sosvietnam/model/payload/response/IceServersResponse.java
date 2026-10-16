package com.sosvietnam.model.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Danh sách máy chủ ICE cho WebRTC (đúng dạng RTCIceServer của trình duyệt):
 * STUN để tìm địa chỉ công cộng, TURN (Cloudflare) để chuyển tiếp khi mạng 4G chặn đường đi thẳng.
 * Mật khẩu TURN là tạm thời (hết hạn sau ttl_seconds), khoá Cloudflare không bao giờ ra khỏi server.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IceServersResponse {

    @JsonProperty("ice_servers")
    private List<IceServer> iceServers;

    @JsonProperty("ttl_seconds")
    private long ttlSeconds;

    /** true khi có TURN; false = chỉ STUN (chưa cấu hình Cloudflare hoặc Cloudflare lỗi). */
    @JsonProperty("relay")
    private boolean relay;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class IceServer {
        @JsonProperty("urls")
        private List<String> urls;
        @JsonProperty("username")
        private String username;
        @JsonProperty("credential")
        private String credential;
    }
}
