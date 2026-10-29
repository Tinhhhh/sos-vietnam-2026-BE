package com.sosvietnam.service;

import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.PhoneLockResponse;
import com.sosvietnam.model.payload.response.PhoneLockSettingsResponse;

import java.util.Collection;
import java.util.Map;

/**
 * Chống báo tin giả theo SĐT (IP chỉ để cảnh báo, không chặn: 4G dùng CGNAT, nhiều người chung 1 IP).
 * - Cán bộ kết luận báo khống 3 ca trong 24 giờ, hoặc người dân huỷ "Bấm nhầm" lần thứ 4 trong 24 giờ: khoá 24 giờ.
 * - Lần khoá thứ 3 trong 30 ngày: khoá vĩnh viễn, chỉ ADMIN gỡ.
 */
public interface PhoneLockService {

    /** Gọi trước khi tạo ca SOS mới: SĐT đang bị khoá -> 403, câu báo có hạn khoá / vĩnh viễn. */
    void checkCanReport(String phone);

    /** Sau khi cán bộ kết luận báo khống 1 ca (huỷ "Báo khống" hoặc kết thúc kết quả FALSE_ALARM). */
    void onFalseAlarm(String phone);

    /** Sau khi người dân tự huỷ với lý do "Bấm nhầm". */
    void onMistakeCancel(String phone);

    /** Số ca báo khống 24 giờ qua theo IP (cờ trên thẻ hàng đợi). */
    Map<String, Long> falseAlarmsByIp(Collection<String> ips);

    /** ADMIN: danh sách lần khoá (active = chỉ khoá đang hiệu lực). */
    ListResponse list(String phone, boolean activeOnly, int pageNo, int pageSize);

    /** ADMIN gỡ khoá (người dân đã trình bày với công an / liên hệ hệ thống). */
    PhoneLockResponse lift(Long id, String note);

    /** ADMIN: công tắc bật / tắt khoá SĐT (app_settings PHONE_LOCK_ENABLED, chưa có dòng = bật). */
    PhoneLockSettingsResponse getSettings();

    PhoneLockSettingsResponse updateSettings(Boolean enabled);
}
