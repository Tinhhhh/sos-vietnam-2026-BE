package com.sosvietnam.model.payload.enums;

import lombok.Getter;

/** Khoá của bảng app_settings (cài đặt chung, sửa được lúc chạy). */
@Getter
public enum AppSettingKey {
    DEMO_GENERATE("Tự động sinh ca [DEMO] mỗi phút"),
    DEMO_HIDDEN("Ẩn ca [DEMO] khỏi hàng đợi và lịch sử");

    private final String description;

    AppSettingKey(String description) {
        this.description = description;
    }
}
