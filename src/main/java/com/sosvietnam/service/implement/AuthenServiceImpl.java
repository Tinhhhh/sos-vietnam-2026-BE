package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;
import com.sosvietnam.model.payload.response.AuthenticationResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RefreshTokenRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.security.JwtTokenProvider;
import com.sosvietnam.service.AuthenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xử lý xác thực: đăng nhập, cấp lại token, đăng ký công khai, lấy tài khoản hiện tại.
 *
 * Quy ước token (giống HIV-TMSS):
 * - JwtTokenProvider chỉ SINH token, không đụng DB.
 * - Service này là nơi DUY NHẤT lưu refresh token, với jitId = jwtId của access token
 *   đi kèm, để CustomLogoutHandler tìm được refresh token từ access token khi logout.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenServiceImpl implements AuthenService {

    // Role mặc định cho tài khoản tự đăng ký qua API công khai
    private static final String PUBLIC_REGISTER_ROLE = "CITIZEN";

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        // 1. Kiểm tra email + mật khẩu (sai sẽ ném BadCredentialsException -> 401)
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword()));

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED,
                        "Authentication failed, account not found"));

        // 2. Tài khoản bị khoá thì không cấp token
        if (account.isLocked()) {
            throw new SosException(HttpStatus.UNAUTHORIZED,
                    "Tài khoản đã bị khoá, vui lòng liên hệ quản trị viên");
        }

        // 3. Sinh cặp token và lưu refresh token (1 lần duy nhất)
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        SecurityContextHolder.getContext().setAuthentication(authentication);
        saveRefreshToken(accessToken, refreshToken);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .build();
    }

    @Override
    public AuthenticationResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        // 1. Lấy refresh token từ header Authorization: Bearer <refresh_token>
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "No JWT token found in request header");
        }

        final String refreshToken = authHeader.substring(7);
        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Token is not a refresh token");
        }
        final String userEmail = jwtTokenProvider.getUsernameFromJwt(refreshToken);

        // 2. Refresh token phải tồn tại trong DB và chưa bị thu hồi / hết hạn
        RefreshToken oldRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED,
                        "Token is invalid or does not exist"));

        if (oldRefreshToken.isRevoked() || oldRefreshToken.isExpired()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Token is invalid or expired");
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        Account account = accountRepository.findByEmail(userEmail)
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED,
                        "Invalid user. User not found"));

        // 3. Thu hồi cặp token cũ (access token cũ vào blacklist, refresh token cũ đánh dấu revoked)
        jwtTokenProvider.revokeAccessToken(account.getId(), oldRefreshToken.getJitId());
        oldRefreshToken.setRevoked(true);
        oldRefreshToken.setExpired(true);
        refreshTokenRepository.save(oldRefreshToken);

        // 4. Cấp cặp token mới
        String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        saveRefreshToken(newAccessToken, newRefreshToken);

        return AuthenticationResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .build();
    }

    @Transactional
    @Override
    public void register(RegistrationRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Email đã tồn tại: " + request.getEmail());
        }

        // Đăng ký công khai luôn là CITIZEN. Không tự tạo role mới:
        // role phải được DataSeeder tạo sẵn, thiếu là lỗi cấu hình server.
        Role role = roleRepository.findByRoleName(PUBLIC_REGISTER_ROLE)
                .orElseThrow(() -> new SosException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Role " + PUBLIC_REGISTER_ROLE + " chưa được khởi tạo"));

        Account account = Account.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(role)
                .province(request.getProvince())
                .ward(request.getWard())
                .isActive(true)
                .isLocked(false)
                .build();

        Account saved = accountRepository.save(account);
        log.info("[USER REGISTERED] Account created: {} with Role: {}", saved.getEmail(), role.getRoleName());
    }

    @Override
    public AccountInfoResponse getCurrentAccount() {
        // SecurityContext đã được JwtAuthenticationFilter gắn từ access token
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        Account account = accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Account not found"));

        return AccountInfoResponse.builder()
                .accountId(account.getId())
                .lastName(account.getLastName())
                .firstName(account.getFirstName())
                .fullName(account.fullName())
                .email(account.getEmail())
                .phone(account.getPhone())
                .address(account.getAddress())
                .agencyType(account.getAgencyType() != null ? account.getAgencyType().name() : null)
                .province(account.getProvince())
                .ward(account.getWard())
                .locked(account.isLocked())
                .createdDate(account.getCreatedDate())
                .roleId(account.getRole() != null ? account.getRole().getId() : null)
                .roleName(account.getRole() != null ? account.getRole().getRoleName() : null)
                .build();
    }

    /** Lưu refresh token, gắn với jwtId của access token đi cùng (dùng khi logout). */
    private void saveRefreshToken(String accessToken, String refreshToken) {
        AccessToken token = jwtTokenProvider.getTokenFromJwt(accessToken);
        RefreshToken newRefreshToken = RefreshToken.builder()
                .token(refreshToken)
                .revoked(false)
                .expired(false)
                .jitId(token.getJwtId())
                .build();

        refreshTokenRepository.save(newRefreshToken);
    }
}
