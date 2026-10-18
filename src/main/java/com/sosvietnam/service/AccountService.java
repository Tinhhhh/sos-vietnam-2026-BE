package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.NewAccountRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;

import java.io.IOException;

/** Quản lý tài khoản (dành cho ADMIN). */
public interface AccountService {
    /** File .xlsx chứa toàn bộ tài khoản, sắp theo vai trò > tỉnh > lực lượng > cấp. */
    byte[] exportAccountsToExcel() throws IOException;

    /** ADMIN tạo tài khoản cán bộ (OFFICER) hoặc quản trị (ADMIN). */
    AccountInfoResponse createAccountByAdmin(NewAccountRequest request);
}
