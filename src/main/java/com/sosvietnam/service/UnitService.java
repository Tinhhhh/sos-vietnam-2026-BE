package com.sosvietnam.service;

import com.sosvietnam.model.payload.request.UnitMemberRequest;
import com.sosvietnam.model.payload.response.ActivationCodeResponse;
import com.sosvietnam.model.payload.response.DutyResponse;
import com.sosvietnam.model.payload.response.UnitMemberResponse;

import java.util.List;
import java.util.UUID;

/**
 * Cài đặt của tài khoản đơn vị: danh sách cán bộ và trực ban. Mọi cán bộ của đơn vị xem được;
 * chỉ chỉ huy hoặc trực ban hiện tại được thêm / sửa / xoá cán bộ, cấp lại PIN, giao trực ban.
 */
public interface UnitService {

    List<UnitMemberResponse> getMembers();

    /** Thêm cán bộ: trả mã kích hoạt 1 lần để cán bộ tự đặt PIN lúc vào trực. */
    ActivationCodeResponse createMember(UnitMemberRequest request);

    UnitMemberResponse updateMember(UUID memberId, UnitMemberRequest request);

    /** Xoá mềm (active = false); phiên đang mở của cán bộ đó bị đăng xuất. */
    void deleteMember(UUID memberId);

    /** Huỷ PIN cũ, mở khoá, sinh mã kích hoạt mới; phiên đang mở bị đăng xuất. */
    ActivationCodeResponse resetPin(UUID memberId);

    DutyResponse getDuty();

    /** Giao trực ban: lượt cũ ghi giờ kết thúc, lượt mới ghi giờ bắt đầu (cùng mốc) + người giao. */
    DutyResponse.Assignment assignDuty(UUID memberId);
}
