package com.sosvietnam.service;

/**
 * SMS cho người báo tin (docs/spec-multi-case.md): tin "đã tiếp nhận" kèm mã ca, gửi 1 lần / ca lúc cán bộ nhận ca
 * lần đầu, sau ~10 giây (ca bị huỷ / báo khống trong lúc đó thì thôi). Tối đa 20 tin / số / 24 giờ; không gửi cho
 * ca demo, ca không có SĐT, hay khi cổng SMS chưa cấu hình.
 */
public interface CitizenSmsService {

    /** Gọi sau khi nhận ca thành công; tự bỏ qua nếu ca đã gửi rồi hoặc không đủ điều kiện. */
    void scheduleReceived(String incidentId);
}
