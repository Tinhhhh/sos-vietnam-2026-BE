package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.CallSignalMessage;
import com.sosvietnam.model.payload.response.IceServersResponse;

import java.security.Principal;

/** Cuộc gọi video WebRTC giữa người báo tin và cán bộ đang giữ ca. */
public interface CallService {

    /**
     * Máy chủ ICE cho cuộc gọi. Cán bộ: đã đăng nhập (JWT). Người dân: incidentId + citizen access token.
     */
    IceServersResponse getIceServers(String incidentId, String citizenAccessToken);

    /**
     * Chuyển tiếp 1 tín hiệu gọi (ring / accept / offer / answer / ice / quality / reject / end)
     * từ người gửi tới đúng bên kia của ca. Sai quyền / ca đã đóng: báo "reject" ngược lại người gửi.
     */
    void relay(Principal sender, CallSignalMessage signal);
}
