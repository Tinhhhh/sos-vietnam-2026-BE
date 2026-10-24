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

    /** Danh sách cán bộ để chọn lúc vào trực (header Authorization: Bearer unit_token). */
    java.util.List<com.sosvietnam.model.payload.response.UnitMemberResponse> getUnitMembers(String unitToken);

    /** Cán bộ nhập PIN (activate = đặt PIN lần đầu bằng mã kích hoạt) -> cặp token của cán bộ. */
    AuthenticationResponse loginMember(String unitToken, com.sosvietnam.model.payload.request.PinLoginRequest request,
                                       boolean activate);

    /** Cán bộ đang trực tự đổi PIN: phiên của riêng cán bộ đó (mọi máy) bị đăng xuất. */
    void changePin(com.sosvietnam.model.payload.request.ChangePinRequest request);
}
