package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.response.IceServersResponse;

import java.security.Principal;

/** Cuộc gọi video WebRTC giữa 3 bên của một ca: người báo tin, cán bộ đang giữ ca, người phụ trách hiện trường. */
public interface CallService {

    /**
     * Máy chủ ICE cho cuộc gọi. Cán bộ: đã đăng nhập (JWT). Người dân: incidentId + citizen access token.
     */
    default IceServersResponse getIceServers(String incidentId, String citizenAccessToken) {
        return getIceServers(incidentId, citizenAccessToken, null);
    }

    /** Thêm người phụ trách hiện trường: fieldToken (mã link /f/{token}). */
    IceServersResponse getIceServers(String incidentId, String citizenAccessToken, String fieldToken);

    /**
     * Chuyển tiếp 1 tín hiệu gọi (ring / accept / offer / answer / ice / quality / reject / end)
     * từ người gửi tới đúng bên kia của ca. Sai quyền / ca đã đóng: báo "reject" ngược lại người gửi.
     */
    void relay(Principal sender, CallSignalMessage signal);
}
