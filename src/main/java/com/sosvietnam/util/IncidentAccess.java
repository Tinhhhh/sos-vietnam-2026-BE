package com.sosvietnam.util;

import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.IncidentDispatch;
import com.sosvietnam.model.entity.RescueStation;
import com.sosvietnam.model.payload.enums.DispatchRole;
import com.sosvietnam.model.payload.enums.DispatchStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.security.OfficerPrincipal;
import lombok.experimental.UtilityClass;

import java.util.Objects;

/**
 * Luật "ai thấy ca nào, ai được nhận ca nào" (module 5), dùng chung cho REST và STOMP.
 * - Lực lượng khác nhau không thấy ca của nhau. ADMIN thấy tất cả.
 * - Cán bộ cấp tỉnh thấy mọi ca của lực lượng mình trong tỉnh.
 * - Cán bộ cấp xã thấy ca xuất phát từ xã mình (kể cả ca đã leo thang, chỉ để theo dõi).
 * - Được nhận ca khi cấp của mình đúng cấp đang giữ ca. ADMIN chỉ nhận ca cấp quốc gia (không xác định được tỉnh).
 * Địa bàn so bằng khoá: mã tỉnh (provinces.code) và id xã (wards.id).
 */
@UtilityClass
public class IncidentAccess {

    public boolean canView(OfficerPrincipal officer, Incident incident) {
        if (officer.isAdmin()) return true;
        if (officer.agency() == null || officer.agency() != incident.getRoutedAgency()) return false;
        if (officer.level() == UnitLevel.PROVINCE) {
            return same(officer.provinceId(), incident.provinceId());
        }
        if (officer.level() == UnitLevel.WARD) {
            // Ca xuất phát ở cấp xã thì có hạn leo thang; ca vào thẳng tỉnh không hiện ở xã
            return incident.getEscalateAt() != null && same(officer.wardId(), incident.wardId());
        }
        return false;
    }

    public boolean canHandle(OfficerPrincipal officer, Incident incident) {
        if (!canView(officer, incident)) return false;
        if (officer.isAdmin()) return incident.getRoutedLevel() == UnitLevel.NATIONAL;
        return officer.level() == incident.getRoutedLevel();
    }

    /**
     * Đơn vị được uỷ quyền (bước 2 điều động): cán bộ cấp xã cùng lực lượng + cùng xã với trạm được chọn
     * thấy ca khi đã có người được giao (assigned_to) và lượt chưa bị từ chối / chưa xong.
     */
    public boolean isDelegatedUnit(OfficerPrincipal officer, IncidentDispatch dispatch) {
        return isStationUnit(officer, dispatch) && dispatch.getAssignedTo() != null
                && DispatchStatus.DELEGATED.contains(dispatch.getStatus());
    }

    /**
     * Cán bộ thuộc đơn vị của trạm của lượt chủ trì (kể cả khi lượt đã bị từ chối / thu hồi): dùng để gỡ thẻ ca
     * khỏi hàng đợi của đơn vị không còn được uỷ quyền.
     */
    public boolean isStationUnit(OfficerPrincipal officer, IncidentDispatch dispatch) {
        if (dispatch == null || dispatch.getRole() != DispatchRole.PRIMARY) return false;
        RescueStation station = dispatch.getStation();
        return officer.level() == UnitLevel.WARD && officer.agency() != null
                && officer.agency() == station.getAgencyType() && same(officer.wardId(), station.wardId());
    }

    /** Cán bộ đang được giao lượt điều động này (người bấm nhận / từ chối / xuất phát). */
    public boolean isAssignee(OfficerPrincipal officer, IncidentDispatch dispatch) {
        return dispatch != null && dispatch.getAssignedTo() != null
                && officer.accountId().equals(dispatch.getAssignedTo().getId());
    }

    /** Hai cán bộ cùng đơn vị (cùng lực lượng, cấp, tỉnh; cấp xã thì cùng xã): được giao ca cho nhau. */
    public boolean sameUnit(OfficerPrincipal a, OfficerPrincipal b) {
        return a.agency() != null && a.agency() == b.agency() && a.level() == b.level()
                && same(a.provinceId(), b.provinceId())
                && (a.level() != UnitLevel.WARD || same(a.wardId(), b.wardId()));
    }

    /** null không bao giờ bằng nhau (chưa xác định địa bàn thì không cho xem nhầm). */
    private static boolean same(Object a, Object b) {
        return a != null && Objects.equals(a, b);
    }
}
