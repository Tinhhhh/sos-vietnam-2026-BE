package com.sosvietnam.service;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;

import java.util.List;

/** Trạng thái cán bộ của đơn vị (trực tuyến, đang giữ ca). */
public interface OfficerService {

    /**
     * Cán bộ cùng đơn vị với người gọi (cùng lực lượng, cấp, tỉnh; cấp xã thì cùng xã), kể cả chính họ.
     * ADMIN: mọi cán bộ đang hoạt động. Sắp xếp: trực tuyến & rảnh trước, rồi đang giữ ca, rồi ngoại tuyến.
     */
    List<OfficerStatusResponse> getUnitOfficers();

    /** Trạng thái (trực tuyến, đang giữ ca) của một danh sách cán bộ, cùng thứ tự sắp xếp như trên. */
    List<OfficerStatusResponse> describe(List<Account> officers);
}
