package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.ChangePasswordRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;
import com.sosvietnam.model.payload.response.AuthenticationResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** Đăng nhập, refresh token, đăng ký công khai và lấy thông tin tài khoản hiện tại. */
public interface AuthenService {
    AuthenticationResponse authenticate(AuthenticationRequest request);

    AuthenticationResponse refreshToken(HttpServletRequest request, HttpServletResponse response);

    void register(RegistrationRequest request);

    AccountInfoResponse getCurrentAccount();

    /** Người đang đăng nhập đổi mật khẩu: phải nhập đúng mật khẩu hiện tại, mật khẩu mới khác mật khẩu cũ. */
    void changePassword(ChangePasswordRequest request);
}
