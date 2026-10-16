package com.sosvietnam.service;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.response.DashboardSummaryResponse;

import java.time.LocalDate;

/** Thống kê sự cố cho tab "Thống Kê" của cán bộ (đề xuất đã duyệt 15/10/2026). */
public interface DashboardService {

    /**
     * Số liệu trong [from, to] (ngày Việt Nam) theo phạm vi người đang đăng nhập.
     * province / agency chỉ có tác dụng với ADMIN (cán bộ luôn bị giới hạn theo tài khoản của mình).
     */
    DashboardSummaryResponse getSummary(LocalDate from, LocalDate to, String province, AgencyType agency);
}
