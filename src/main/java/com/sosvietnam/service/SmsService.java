package com.sosvietnam.service;

/**
 * Gửi SMS (hiện là app SMS Gateway for Android chế độ Cloud, {@code app.sms.gateway.*}): điện thoại Android cắm SIM
 * của đơn vị nhận lệnh từ máy chủ api.sms-gate.app rồi tự gửi tin bằng SIM đó.
 */
public interface SmsService {

    /** sent = cổng SMS đã nhận tin (điện thoại gửi đi sau, thường vài giây); error = lý do không gửi được. */
    record Result(boolean sent, String error) {
    }

    /** Đã điền tài khoản cổng SMS chưa (trống = không gửi, chỉ ghi log). */
    boolean configured();

    /** phone dạng Việt Nam (0912345678, +84912345678...); text nên không dấu để vừa 1 tin 160 ký tự. */
    Result send(String phone, String text);
}
