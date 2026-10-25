package com.sosvietnam.service;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.request.NewAccountRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;

import java.io.IOException;

/** Quản lý tài khoản (dành cho ADMIN). */
public interface AccountService {
    /**
     * File .xlsx: sheet tài khoản (sắp theo vai trò > tỉnh > lực lượng > cấp) + sheet cán bộ của đơn vị.
     * Bộ lọc tuỳ chọn, null = không lọc.
     */
    byte[] exportAccountsToExcel(String provinceId, AgencyType agency, UnitLevel level) throws IOException;

    /** ADMIN tạo tài khoản cán bộ (OFFICER) hoặc quản trị (ADMIN). */
    AccountInfoResponse createAccountByAdmin(NewAccountRequest request);
}
