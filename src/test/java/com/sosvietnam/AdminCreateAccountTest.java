package com.sosvietnam;

import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.enums.AgencyType;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.enums.UnitLevel;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.NewAccountRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.service.implement.AccountServiceImpl;
import com.sosvietnam.util.AccountExcelExporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** POST /api/v1/admin/accounts: kiểm tra lực lượng/cấp, xã thuộc tỉnh, address chỉ giữ phần chi tiết. */
class AdminCreateAccountTest {

    final AccountRepository accounts = mock(AccountRepository.class);
    final RoleRepository roles = mock(RoleRepository.class);
    final ProvinceRepository provinces = mock(ProvinceRepository.class);
    final WardRepository wards = mock(WardRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final AccountServiceImpl service = new AccountServiceImpl(accounts, mock(AccountExcelExporter.class), encoder,
            roles, provinces, wards, mock(com.sosvietnam.repository.UnitMemberRepository.class),
            mock(com.sosvietnam.repository.UnitDutyAssignmentRepository.class));

    @BeforeEach
    void setUp() {
        when(accounts.existsByEmail(anyString())).thenReturn(false);
        when(accounts.save(any())).thenAnswer(i -> i.getArgument(0));
        when(encoder.encode(anyString())).thenReturn("hash");
        when(roles.findByRoleName(anyString())).thenAnswer(i ->
                Optional.of(Role.builder().id(1L).roleName(i.getArgument(0)).build()));
        when(provinces.findById(anyString())).thenAnswer(i -> TestGeo.provinces().stream()
                .filter(p -> p.getCode().equals(i.getArgument(0))).findFirst());
        when(wards.findById(TestGeo.TAN_AN.getId())).thenReturn(Optional.of(TestGeo.TAN_AN));
        when(wards.findById(TestGeo.HOAN_KIEM.getId())).thenReturn(Optional.of(TestGeo.HOAN_KIEM));
    }

    NewAccountRequest.NewAccountRequestBuilder officer() {
        return NewAccountRequest.builder().lastName("Nguyễn Văn").firstName("An").email(" CongAn.Moi@SOS.vn ")
                .password("12345678").phone("0912345678").address("02 Nguyễn Tri Phương, Kv.10")
                .roleName(RoleName.OFFICER).agencyType(AgencyType.POLICE).unitLevel(UnitLevel.WARD)
                .provinceId("92").wardId(TestGeo.TAN_AN.getId());
    }

    @Test
    void createsWardPoliceOfficer_withFullAddressFromWardAndProvince() {
        AccountInfoResponse res = service.createAccountByAdmin(officer().build());

        assertThat(res.getEmail()).isEqualTo("congan.moi@sos.vn");
        assertThat(res.getAddress()).isEqualTo("02 Nguyễn Tri Phương, Kv.10");
        assertThat(res.getFullAddress()).isEqualTo("02 Nguyễn Tri Phương, Kv.10, Phường Tân An, Cần Thơ");
        assertThat(res.getAgencyType()).isEqualTo("POLICE");
        assertThat(res.getUnitLevel()).isEqualTo("WARD");
        assertThat(res.getRoleName()).isEqualTo("OFFICER");
        assertThat(res.getProvinceId()).isEqualTo("92");
        assertThat(res.getWardId()).isEqualTo(TestGeo.TAN_AN.getId());
        verify(encoder).encode("12345678");
    }

    @Test
    void admin_hasNoAgencyAndNationalLevel() {
        AccountInfoResponse res = service.createAccountByAdmin(officer().roleName(RoleName.ADMIN)
                .agencyType(AgencyType.FIRE).unitLevel(UnitLevel.WARD).build());
        assertThat(res.getAgencyType()).isNull();
        assertThat(res.getUnitLevel()).isEqualTo("NATIONAL");
    }

    @Test
    void duplicateEmail_isConflict() {
        when(accounts.existsByEmail("congan.moi@sos.vn")).thenReturn(true);
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().build()))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.CONFLICT);
        verify(accounts, never()).save(any(Account.class));
    }

    @Test
    void citizenRole_isRejected() {
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().roleName(RoleName.CITIZEN).build()))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void officerWithoutAgency_isRejected() {
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().agencyType(null).build()))
                .isInstanceOf(SosException.class).hasMessageContaining("lực lượng");
    }

    @Test
    void hospitalWardLevel_isRejected() {
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().agencyType(AgencyType.HOSPITAL).build()))
                .isInstanceOf(SosException.class).extracting("httpStatus").isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void wardOutsideProvince_isRejected() {
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().wardId(TestGeo.HOAN_KIEM.getId()).build()))
                .isInstanceOf(SosException.class).hasMessageContaining("không thuộc tỉnh");
    }

    @Test
    void unknownProvince_isRejected() {
        assertThatThrownBy(() -> service.createAccountByAdmin(officer().provinceId("99").build()))
                .isInstanceOf(SosException.class).hasMessageContaining("Mã tỉnh");
    }
}
