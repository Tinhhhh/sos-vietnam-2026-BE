package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Incident;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.EmergencyType;
import com.sosvietnam.model.payload.enums.IncidentStatus;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.response.ListResponse;
import com.sosvietnam.model.payload.response.OfficerStatusResponse;
import com.sosvietnam.repository.IncidentRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.UnitMemberRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.IncidentNotifier;
import com.sosvietnam.service.implement.OfficerServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * GET /officers/status: one page of one unit (the WHERE itself runs in the DB, UnitMemberSpecification);
 * online from STOMP, busy from held incidents, page sorted free-online first; ADMIN must pick one unit.
 */
class OfficerStatusTest {

    final UnitMemberRepository members = mock(UnitMemberRepository.class);
    final IncidentRepository incidents = mock(IncidentRepository.class);
    final IncidentNotifier notifier = mock(IncidentNotifier.class);
    final ProvinceRepository provinces = mock(ProvinceRepository.class);
    final WardRepository wards = mock(WardRepository.class);
    final OfficerServiceImpl service = new OfficerServiceImpl(members, new com.sosvietnam.security.CurrentOfficer(),
            incidents, notifier, provinces, wards);
    final Role officerRole = Role.builder().id(2L).roleName("OFFICER").build();
    final Role adminRole = Role.builder().id(1L).roleName("ADMIN").build();

    Account officer(String email, String first, String ward) {
        return Account.builder().id(UUID.randomUUID()).email(email).firstName(first).lastName("Cán Bộ").role(officerRole)
                .agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD).province(TestGeo.province("Cần Thơ"))
                .ward(TestGeo.ward(ward)).isActive(true).build();
    }

    Account admin() {
        return Account.builder().id(UUID.randomUUID()).email("admin@sos.vn").firstName("Quản Trị").role(adminRole)
                .unitLevel(UnitLevel.NATIONAL).isActive(true).build();
    }

    @SuppressWarnings("unchecked")
    void pageOf(List<UnitMember> list) {
        when(members.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(inv -> new PageImpl<>(list, inv.getArgument(1), list.size()));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void officer_pageOfMyUnit_onlineAndBusy_sortedFreeOnlineFirst_unitFiltersIgnored() {
        Account me = officer("me@sos.vn", "An", "Phường Tân An");
        Account free = officer("free@sos.vn", "Bình", "Phường Tân An");
        Account busy = officer("busy@sos.vn", "Cường", "Phường Tân An");
        Account offline = officer("off@sos.vn", "Dũng", "Phường Tân An");
        pageOf(java.util.stream.Stream.of(offline, busy, me, free).map(TestGeo::member).toList());
        when(incidents.findByHandlerIdInAndStatusIn(any(), any())).thenReturn(List.of(
                Incident.builder().id("SOS-9").handlerId(busy.getId()).status(IncidentStatus.VERIFYING)
                        .emergencyType(EmergencyType.POLICE).build()));
        when(notifier.onlineMemberIds()).thenReturn(Set.of(me.getId(), free.getId(), busy.getId()));
        TestGeo.signIn(me);

        // an officer cannot look at another unit: level / province / ward are ignored, no 400
        ListResponse page = service.getUnitOfficers(UnitLevel.PROVINCE, "01", 101L, AgencyType.FIRE, null, 0, 50);

        @SuppressWarnings("unchecked")
        List<OfficerStatusResponse> list = (List<OfficerStatusResponse>) page.getContent();
        assertThat(list).extracting(OfficerStatusResponse::getEmail)
                .containsExactly("me@sos.vn", "free@sos.vn", "busy@sos.vn", "off@sos.vn");
        assertThat(list.get(0).isMe()).isTrue();
        OfficerStatusResponse b = list.get(2);
        assertThat(b.isOnline()).isTrue();
        assertThat(b.isBusy()).isTrue();
        assertThat(b.getHeldIncident().getId()).isEqualTo("SOS-9");
        assertThat(b.getUnitLabel()).contains("Công An");
        assertThat(list.get(3).isOnline()).isFalse();
        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.isLast()).isTrue();
        verifyNoInteractions(provinces, wards);
    }

    @Test
    @SuppressWarnings("unchecked")
    void pageSizeIsCappedAt100_sortedByUnitThenRoleThenName() {
        pageOf(List.of());
        TestGeo.signIn(officer("me@sos.vn", "An", "Phường Tân An"));

        service.getUnitOfficers(null, null, null, null, null, 2, 5000);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(members).findAll(any(Specification.class), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(2);
        assertThat(pageable.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageable.getValue().getSort().toString()).contains("account.email", "unitRole", "fullName");
    }

    @Test
    void provinceOfficer_canPickAWardOfTheirProvince_wardOfficerCannot() {
        Account province = officer("congan.cantho@sos.vn", "Tỉnh", "Phường Cái Khế");
        province.setUnitLevel(UnitLevel.PROVINCE);
        TestGeo.signIn(province);
        when(wards.findById(TestGeo.TAN_AN.getId())).thenReturn(Optional.of(TestGeo.TAN_AN));
        when(wards.findById(TestGeo.HOAN_KIEM.getId())).thenReturn(Optional.of(TestGeo.HOAN_KIEM));
        pageOf(List.of());

        service.getUnitOfficers(UnitLevel.WARD, null, TestGeo.TAN_AN.getId(), null, null, 0, 50);
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.WARD, null, TestGeo.HOAN_KIEM.getId(), null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("không thuộc tỉnh");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.WARD, null, null, null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("ward_id");
        service.getUnitOfficers(null, null, null, null, null, 0, 50); // own province unit

        // a ward officer asking for another ward just gets their own unit (ward not even looked up)
        clearInvocations(wards);
        TestGeo.signIn(officer("congan.tanan@sos.vn", "Xã", "Phường Tân An"));
        service.getUnitOfficers(UnitLevel.WARD, null, TestGeo.CAI_KHE.getId(), null, null, 0, 50);
        verifyNoInteractions(wards);
    }

    @Test
    void admin_mustPickOneUnit() {
        TestGeo.signIn(admin());
        when(provinces.existsById("92")).thenReturn(true);
        when(wards.findById(TestGeo.TAN_AN.getId())).thenReturn(Optional.of(TestGeo.TAN_AN));
        when(wards.findById(TestGeo.HOAN_KIEM.getId())).thenReturn(Optional.of(TestGeo.HOAN_KIEM));
        pageOf(List.of());

        assertThatThrownBy(() -> service.getUnitOfficers(null, null, null, null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("cấp");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.NATIONAL, "92", null, null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("cấp");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.PROVINCE, "99", null, null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("province_id");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.WARD, "92", null, null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("ward_id");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.WARD, "92", TestGeo.HOAN_KIEM.getId(), null, null, 0, 50))
                .isInstanceOf(SosException.class).hasMessageContaining("không thuộc tỉnh");
        assertThatThrownBy(() -> service.getUnitOfficers(UnitLevel.PROVINCE, "92", null, null, null, -1, 50))
                .isInstanceOf(SosException.class);
        verify(members, never()).findAll(any(Specification.class), any(Pageable.class));

        // valid: province unit, and ward unit with a ward of that province
        service.getUnitOfficers(UnitLevel.PROVINCE, "92", null, AgencyType.POLICE, null, 0, 50);
        service.getUnitOfficers(UnitLevel.WARD, "92", TestGeo.TAN_AN.getId(), null, true, 0, 50);
        verify(members, times(2)).findAll(any(Specification.class), any(Pageable.class));
    }
}
