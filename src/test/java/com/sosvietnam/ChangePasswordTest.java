package com.sosvietnam;

import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.payload.request.ChangePasswordRequest;
import com.sosvietnam.repository.*;
import com.sosvietnam.security.JwtTokenProvider;
import com.sosvietnam.security.StompSessionRegistry;
import com.sosvietnam.service.implement.AuthenServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

/** POST /auth/change-password: đúng mật khẩu hiện tại mới đổi, mật khẩu mới phải khác. */
class ChangePasswordTest {

    final AccountRepository accounts = mock(AccountRepository.class);
    final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4);
    final JwtTokenProvider jwt = mock(JwtTokenProvider.class);
    final StompSessionRegistry stomp = mock(StompSessionRegistry.class);
    final AuthenServiceImpl service = new AuthenServiceImpl(mock(AuthenticationManager.class), accounts,
            mock(ProvinceRepository.class), mock(WardRepository.class), mock(RoleRepository.class),
            mock(RefreshTokenRepository.class), encoder, stomp, jwt, mock(UserDetailsService.class),
            mock(com.sosvietnam.repository.UnitMemberRepository.class),
            mock(com.sosvietnam.repository.UnitDutyAssignmentRepository.class), new com.sosvietnam.security.CurrentOfficer());
    Account me;

    @BeforeEach
    void setUp() {
        me = Account.builder().id(java.util.UUID.randomUUID()).email("me@sos.vn").password(encoder.encode("old-pass-1"))
                .role(com.sosvietnam.model.entity.Role.builder().roleName("ADMIN").build()).build();
        when(accounts.findById(me.getId())).thenReturn(Optional.of(me));
        TestGeo.signIn(me);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void changesWhenCurrentMatches() {
        service.changePassword(new ChangePasswordRequest("old-pass-1", "new-pass-2"));
        assertThat(encoder.matches("new-pass-2", me.getPassword())).isTrue();
        verify(accounts).save(me);
        // mọi phiên đang đăng nhập của tài khoản bị đá văng
        verify(jwt).disableOldTokens(me.getId());
        // màn hình đang mở trên các máy khác mất kết nối STOMP ngay
        verify(stomp).closeWhere(any());
    }

    @Test
    void unitAccount_onlyTheCommanderMayChangeIt() {
        me.setRole(com.sosvietnam.model.entity.Role.builder().roleName("OFFICER").build());
        TestGeo.signIn(me); // cán bộ thường (MEMBER)
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("old-pass-1", "new-pass-2")))
                .isInstanceOf(SosException.class).hasMessageContaining("chỉ huy");
        verify(accounts, never()).save(any());
    }

    @Test
    void wrongCurrent_400() {
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("nope", "new-pass-2")))
                .isInstanceOf(SosException.class).hasMessageContaining("hiện tại không đúng");
        verify(accounts, never()).save(any());
        verify(jwt, never()).disableOldTokens(any());
    }

    @Test
    void sameAsOld_400() {
        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("old-pass-1", "old-pass-1")))
                .isInstanceOf(SosException.class).hasMessageContaining("khác");
    }
}
