package com.sosvietnam.service;

import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;

import java.util.List;

/** Trạng thái cán bộ của đơn vị (trực tuyến, đang giữ ca). */
public interface OfficerService {

    /**
     * Một trang cán bộ của MỘT đơn vị (trạm), kèm trạng thái.
     * OFFICER: luôn là đơn vị của người gọi (cùng lực lượng, cấp, tỉnh; cấp xã thì cùng xã), kể cả chính họ;
     * level / province / ward / agency bị bỏ qua; riêng cán bộ cấp tỉnh được chọn level = WARD + wardId
     * (xã trong tỉnh mình) để xem đơn vị xã cùng lực lượng.
     * ADMIN: bắt buộc chọn level (PROVINCE | WARD) + provinceId, cấp xã thêm wardId (xã phải thuộc tỉnh);
     * agency tuỳ chọn. online = true: chỉ người đang mở bàn chỉ huy.
     * Trong trang: trực tuyến & rảnh trước, rồi đang giữ ca, rồi ngoại tuyến.
     */
    ListResponse getUnitOfficers(UnitLevel level, String provinceId, Long wardId, AgencyType agency, Boolean online,
                                 int pageNo, int pageSize);

    /** Trạng thái (trực tuyến, đang giữ ca) của một danh sách cán bộ, cùng thứ tự sắp xếp như trên. */
    List<OfficerStatusResponse> describe(List<com.sosvietnam.model.entity.UnitMember> officers);
}
