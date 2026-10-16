package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.DashboardSummaryResponse;
import com.sosvietnam.model.payload.response.DashboardSummaryResponse.*;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.DashboardRepository;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.service.DashboardService;
import com.sosvietnam.util.IncidentAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Phạm vi giống quyền xem ca ở hàng đợi (IncidentAccess):
 * - cán bộ cấp xã: ca của lực lượng mình xuất phát ở xã mình;
 * - cán bộ cấp tỉnh: mọi ca của lực lượng mình trong tỉnh, chia theo xã/phường;
 * - ADMIN: toàn quốc (chia theo tỉnh, theo lực lượng), lọc được theo tỉnh / lực lượng.
 */
@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_RANGE_DAYS = 366;
    private static final int TOP_TAGS = 10;
    private static final String UNTAGGED = "Chưa chọn loại";
    private static final String UNKNOWN_AREA = "Chưa xác định";

    private final DashboardRepository dashboardRepository;
    private final AccountRepository accountRepository;

    @Value("${app.incident.escalation-minutes:15}")
    private long slaMinutes;

    @Override
    public DashboardSummaryResponse getSummary(LocalDate from, LocalDate to, String province, AgencyType agency) {
        OfficerPrincipal me = currentOfficer();

        LocalDate today = LocalDate.now(VN);
        LocalDate end = to != null ? to : today;
        LocalDate start = from != null ? from : end.minusDays(6);
        if (start.isAfter(end)) {
            throw new SosException("Ngày bắt đầu phải trước hoặc bằng ngày kết thúc");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        if (days > MAX_RANGE_DAYS) {
            throw new SosException("Khoảng thời gian tối đa " + MAX_RANGE_DAYS + " ngày");
        }

        Scope scope = scopeOf(me, province, agency);
        DashboardRepository.Filter current = filter(scope, start, end);
        LocalDate prevEnd = start.minusDays(1);
        LocalDate prevStart = start.minusDays(days);
        DashboardRepository.Filter previous = filter(scope, prevStart, prevEnd);

        return DashboardSummaryResponse.builder()
                .scope(scope)
                .range(new Range(start, end, prevStart, prevEnd))
                .kpis(kpis(current, dashboardRepository.count(previous), dashboardRepository.pendingNow(current)))
                .byTag(byTag(current))
                .byHour(byHour(current))
                .byDay(byDay(current, start, end))
                .heatmap(heatmap(current))
                .byArea(byArea(current, scope.getLevel()))
                .byAgency(scope.getLevel() == UnitLevel.WARD ? List.of() : byAgency(allForces(current)))
                .build();
    }

    /** Cán bộ: theo tài khoản (bỏ qua tham số lọc). ADMIN: toàn quốc, hoặc một tỉnh nếu truyền province. */
    static Scope scopeOf(OfficerPrincipal me, String province, AgencyType agency) {
        if (me.isAdmin()) {
            boolean oneProvince = StringUtils.hasText(province);
            return new Scope(agency, oneProvince ? UnitLevel.PROVINCE : UnitLevel.NATIONAL,
                    oneProvince ? province.trim() : null, null);
        }
        UnitLevel level = me.level() == null ? UnitLevel.PROVINCE : me.level();
        return new Scope(me.agency(), level,
                level == UnitLevel.NATIONAL ? null : me.province(),
                level == UnitLevel.WARD ? me.ward() : null);
    }

    private DashboardRepository.Filter filter(Scope scope, LocalDate from, LocalDate to) {
        ZoneId server = ZoneId.systemDefault();
        // Ngày Việt Nam -> mốc giờ của server (cột created_at lưu giờ server)
        LocalDateTime startAt = from.atStartOfDay(VN).withZoneSameInstant(server).toLocalDateTime();
        LocalDateTime endAt = to.plusDays(1).atStartOfDay(VN).withZoneSameInstant(server).toLocalDateTime();
        return new DashboardRepository.Filter(
                scope.getAgency(),
                scope.getLevel() == UnitLevel.NATIONAL ? null : IncidentAccess.provinceKey(scope.getProvince()),
                scope.getLevel() == UnitLevel.WARD ? IncidentAccess.key(scope.getWard()) : null,
                scope.getLevel() == UnitLevel.WARD,
                startAt, endAt, server.getId(), slaMinutes);
    }

    private Kpis kpis(DashboardRepository.Filter f, long previousTotal, long pendingNow) {
        Map<String, Object> row = dashboardRepository.kpis(f);
        long accepted = asLong(row.get("accepted"));
        return Kpis.builder()
                .total(asLong(row.get("total")))
                .previousTotal(previousTotal)
                .pending(pendingNow)
                .accepted(accepted)
                .escalated(asLong(row.get("escalated")))
                .avgAcceptSeconds(asDouble(row.get("avg_accept")))
                .medianAcceptSeconds(asDouble(row.get("median_accept")))
                .withinSlaRate(accepted == 0 ? null : (double) asLong(row.get("within_sla")) / accepted)
                .slaMinutes(slaMinutes)
                // Chưa có module đóng ca (hoàn tất / báo khống)
                .fakeAlarmCount(null)
                .resolvedCount(null)
                .build();
    }

    private List<TagCount> byTag(DashboardRepository.Filter f) {
        return dashboardRepository.byTag(f, TOP_TAGS).stream()
                .map(r -> new TagCount(r.get("tag") == null ? UNTAGGED : r.get("tag").toString(), asLong(r.get("cnt"))))
                .toList();
    }

    private List<Long> byHour(DashboardRepository.Filter f) {
        Long[] hours = new Long[24];
        Arrays.fill(hours, 0L);
        for (Map<String, Object> r : dashboardRepository.byHour(f)) {
            hours[((Number) r.get("hour")).intValue()] = asLong(r.get("cnt"));
        }
        return List.of(hours);
    }

    private List<DayCount> byDay(DashboardRepository.Filter f, LocalDate from, LocalDate to) {
        Map<LocalDate, DayCount> byDate = new HashMap<>();
        for (Map<String, Object> r : dashboardRepository.byDay(f)) {
            LocalDate day = toLocalDate(r.get("day"));
            long accepted = asLong(r.get("accepted"));
            byDate.put(day, new DayCount(day, asLong(r.get("cnt")), asLong(r.get("escalated")), accepted,
                    accepted == 0 ? null : (double) asLong(r.get("within_sla")) / accepted));
        }
        List<DayCount> days = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            days.add(byDate.getOrDefault(d, new DayCount(d, 0, 0)));
        }
        return days;
    }

    private List<List<Long>> heatmap(DashboardRepository.Filter f) {
        long[][] grid = new long[7][24];
        for (Map<String, Object> r : dashboardRepository.heatmap(f)) {
            grid[((Number) r.get("dow")).intValue() - 1][((Number) r.get("hour")).intValue()] = asLong(r.get("cnt"));
        }
        List<List<Long>> rows = new ArrayList<>(7);
        for (long[] row : grid) {
            rows.add(Arrays.stream(row).boxed().toList());
        }
        return rows;
    }

    private List<AreaStats> byArea(DashboardRepository.Filter f, UnitLevel level) {
        if (level == UnitLevel.WARD) return List.of();
        String column = level == UnitLevel.PROVINCE ? "ward" : "province";
        return dashboardRepository.byArea(f, column).stream().map(r -> {
            long accepted = asLong(r.get("accepted"));
            return AreaStats.builder()
                    .name(r.get("name") == null ? UNKNOWN_AREA : r.get("name").toString())
                    .total(asLong(r.get("total")))
                    .pending(asLong(r.get("pending")))
                    .escalated(asLong(r.get("escalated")))
                    .avgAcceptSeconds(asDouble(r.get("avg_accept")))
                    .withinSlaRate(accepted == 0 ? null : (double) asLong(r.get("within_sla")) / accepted)
                    .build();
        }).toList();
    }

    /** Cùng địa bàn và khoảng ngày nhưng mọi lực lượng (chỉ dùng để đếm theo lực lượng). */
    static DashboardRepository.Filter allForces(DashboardRepository.Filter f) {
        return new DashboardRepository.Filter(null, f.provinceKey(), f.wardKey(), f.wardOriginOnly(),
                f.start(), f.end(), f.serverZone(), f.slaMinutes());
    }

    private List<AgencyCount> byAgency(DashboardRepository.Filter f) {
        return dashboardRepository.byAgency(f).stream()
                .map(r -> new AgencyCount(AgencyType.valueOf(r.get("agency").toString()), asLong(r.get("cnt"))))
                .toList();
    }

    private OfficerPrincipal currentOfficer() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Chưa đăng nhập");
        }
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
        OfficerPrincipal officer = OfficerPrincipal.of(account);
        if (!officer.isStaff()) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ cán bộ mới xem được thống kê");
        }
        return officer;
    }

    private static long asLong(Object v) {
        return v == null ? 0 : ((Number) v).longValue();
    }

    private static Double asDouble(Object v) {
        return v == null ? null : ((Number) v).doubleValue();
    }

    private static LocalDate toLocalDate(Object v) {
        if (v instanceof java.sql.Date d) return d.toLocalDate();
        if (v instanceof LocalDate d) return d;
        return LocalDate.parse(v.toString());
    }
}
