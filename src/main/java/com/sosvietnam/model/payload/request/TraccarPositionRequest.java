package com.sosvietnam.model.payload.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Một điểm GPS Traccar đẩy sang (position forwarding, forward.type = json): { position: {...}, device: {...} }.
 * Tên trường của Traccar là camelCase và có rất nhiều trường khác, nên ở đây ghi tên rõ và bỏ qua trường lạ
 * (khác quy ước "field lạ -> 400" của API SOS).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TraccarPositionRequest {

    @JsonProperty("position")
    private Position position;

    @JsonProperty("device")
    private Device device;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Position {
        @JsonProperty("latitude")
        private Double latitude;
        @JsonProperty("longitude")
        private Double longitude;
        /** Knot (hải lý / giờ), như Traccar lưu. */
        @JsonProperty("speed")
        private Double speed;
        /** Hướng đi, độ (0 = bắc). */
        @JsonProperty("course")
        private Double course;
        @JsonProperty("accuracy")
        private Double accuracy;
        /** Thời điểm điện thoại lấy được vị trí, chuỗi ISO có offset. */
        @JsonProperty("fixTime")
        private String fixTime;
        @JsonProperty("valid")
        private Boolean valid;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Device {
        /** Mã thiết bị đặt trong app Traccar Client = số hiệu cán bộ (unit_members.badge_number). */
        @JsonProperty("uniqueId")
        private String uniqueId;
    }
}
