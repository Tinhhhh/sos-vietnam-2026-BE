package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.DashboardSummaryResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.DashboardRepository;
import com.sosvietnam.service.implement.DashboardServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Phạm vi và cách ghép số liệu của DashboardServiceImpl (SQL được thử riêng với DB thật). */
class DashboardServiceTest {

    final DashboardRepository repo = mock(DashboardRepository.class);
    final AccountRepository accounts = mock(AccountRepository.class);
    final DashboardServiceImpl service = new DashboardServiceImpl(repo, accounts);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "slaMinutes", 15L);
        Map<String, Object> empty = new HashMap<>();
        empty.put("total", 0L);
        empty.put("accepted", 0L);
        empty.put("escalated", 0L);
        empty.put("avg_accept", null);
        empty.put("median_accept", null);
        empty.put("within_sla", 0L);
        when(repo.kpis(any())).thenReturn(empty);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    void loginAs(String role, AgencyType agency, UnitLevel level, String province, String ward) {
        Account a = Account.builder().id(UUID.randomUUID()).email("x@sos.vn").firstName("A").lastName("B")
                .role(Role.builder().roleName(role).build()).agencyType(agency).unitLevel(level)
                .province(province).ward(ward).isActive(true).build();
        when(accounts.findByEmail("x@sos.vn")).thenReturn(Optional.of(a));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x@sos.vn", null, role));
    }

    DashboardRepository.Filter currentFilter() {
        ArgumentCaptor<DashboardRepository.Filter> f = ArgumentCaptor.forClass(DashboardRepository.Filter.class);
        verify(repo).kpis(f.capture());
        return f.getValue();
    }

    @Test
    void wardOfficer_isLimitedToOwnAgencyAndWard_ignoringFilters() {
        loginAs("OFFICER", AgencyType.POLICE, UnitLevel.WARD, "Cần Thơ", "Phường Tân An");
        DashboardSummaryResponse r = service.getSummary(null, null, "Hà Nội", AgencyType.FIRE);

        assertThat(r.getScope().getLevel()).isEqualTo(UnitLevel.WARD);
        DashboardRepository.Filter f = currentFilter();
        assertThat(f.agency()).isEqualTo(AgencyType.POLICE);
        assertThat(f.provinceKey()).isEqualTo("can tho");
        assertThat(f.wardKey()).isEqualTo("phuong tan an");
        assertThat(f.wardOriginOnly()).isTrue();
        assertThat(r.getByArea()).isEmpty();
        assertThat(r.getByAgency()).isEmpty();
        verify(repo, never()).byArea(any(), any());
        verify(repo, never()).byAgency(any());
    }

    @Test
    void provinceOfficer_groupsByWard() {
        loginAs("OFFICER", AgencyType.CSGT, UnitLevel.PROVINCE, "TP. Cần Thơ", null);
        service.getSummary(null, null, null, null);
        DashboardRepository.Filter f = currentFilter();
        assertThat(f.agency()).isEqualTo(AgencyType.CSGT);
        assertThat(f.provinceKey()).isEqualTo("can tho");
        assertThat(f.wardKey()).isNull();
        verify(repo).byArea(any(), eq("ward"));
        // Biểu đồ tròn theo lực lượng: mọi lực lượng trong tỉnh, không chỉ CSGT
        ArgumentCaptor<DashboardRepository.Filter> agencyFilter = ArgumentCaptor.forClass(DashboardRepository.Filter.class);
        verify(repo).byAgency(agencyFilter.capture());
        assertThat(agencyFilter.getValue().agency()).isNull();
        assertThat(agencyFilter.getValue().provinceKey()).isEqualTo("can tho");
    }

    @Test
    void admin_isNational_orOneProvinceWhenFiltered() {
        loginAs("ADMIN", null, UnitLevel.NATIONAL, "TP. Hà Nội", null);
        DashboardSummaryResponse national = service.getSummary(null, null, null, null);
        assertThat(national.getScope().getLevel()).isEqualTo(UnitLevel.NATIONAL);
        assertThat(currentFilter().provinceKey()).isNull();
        verify(repo).byArea(any(), eq("province"));
        verify(repo).byAgency(any());

        reset(repo);
        setUp();
        DashboardSummaryResponse cantho = service.getSummary(null, null, "Cần Thơ", AgencyType.POLICE);
        assertThat(cantho.getScope().getLevel()).isEqualTo(UnitLevel.PROVINCE);
        assertThat(currentFilter().agency()).isEqualTo(AgencyType.POLICE);
        verify(repo).byArea(any(), eq("ward"));
    }

    @Test
    void emptyRange_hasZerosNotFakeNumbers_andEveryDayListed() {
        loginAs("OFFICER", AgencyType.POLICE, UnitLevel.PROVINCE, "Cần Thơ", null);
        Map<String, Object> day = new HashMap<>();
        day.put("day", Date.valueOf(LocalDate.of(2026, 10, 14)));
        day.put("cnt", 3L);
        day.put("escalated", 1L);
        day.put("accepted", 2L);
        day.put("within_sla", 1L);
        when(repo.byDay(any())).thenReturn(List.of(day));

        DashboardSummaryResponse r = service.getSummary(LocalDate.of(2026, 10, 13), LocalDate.of(2026, 10, 15), null, null);

        assertThat(r.getKpis().getAvgAcceptSeconds()).isNull();
        assertThat(r.getKpis().getWithinSlaRate()).isNull();
        assertThat(r.getKpis().getFakeAlarmCount()).isNull();
        assertThat(r.getByHour()).hasSize(24).containsOnly(0L);
        assertThat(r.getHeatmap()).hasSize(7).allSatisfy(row -> assertThat(row).hasSize(24));
        assertThat(r.getByDay()).extracting(DashboardSummaryResponse.DayCount::getTotal).containsExactly(0L, 3L, 0L);
        // Tỷ lệ nhận trong 15' theo ngày: null khi ngày đó chưa ca nào được nhận
        assertThat(r.getByDay()).extracting(DashboardSummaryResponse.DayCount::getWithinSlaRate).containsExactly(null, 0.5, null);
        // kỳ trước cùng độ dài, liền trước
        assertThat(r.getRange().getPreviousFrom()).isEqualTo(LocalDate.of(2026, 10, 10));
        assertThat(r.getRange().getPreviousTo()).isEqualTo(LocalDate.of(2026, 10, 12));
    }

    @Test
    void slaRate_isWithinOverAccepted() {
        loginAs("OFFICER", AgencyType.POLICE, UnitLevel.PROVINCE, "Cần Thơ", null);
        Map<String, Object> k = new HashMap<>();
        k.put("total", 10L);
        k.put("accepted", 8L);
        k.put("escalated", 1L);
        k.put("avg_accept", 245.0);
        k.put("median_accept", 160.0);
        k.put("within_sla", 6L);
        when(repo.kpis(any())).thenReturn(k);
        when(repo.count(any())).thenReturn(4L);
        when(repo.pendingNow(any())).thenReturn(2L);

        var kpis = service.getSummary(null, null, null, null).getKpis();
        assertThat(kpis.getWithinSlaRate()).isEqualTo(0.75);
        assertThat(kpis.getPreviousTotal()).isEqualTo(4);
        assertThat(kpis.getPending()).isEqualTo(2);
        assertThat(kpis.getSlaMinutes()).isEqualTo(15);
    }

    @Test
    void badRanges_areRejected() {
        loginAs("OFFICER", AgencyType.POLICE, UnitLevel.PROVINCE, "Cần Thơ", null);
        assertThatThrownBy(() -> service.getSummary(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(SosException.class);
        assertThatThrownBy(() -> service.getSummary(LocalDate.of(2024, 1, 1), LocalDate.of(2026, 10, 1), null, null))
                .isInstanceOf(SosException.class);
    }

    @Test
    void citizens_cannotSeeStatistics() {
        loginAs("CITIZEN", null, null, null, null);
        assertThatThrownBy(() -> service.getSummary(null, null, null, null)).isInstanceOf(SosException.class);
    }
}
