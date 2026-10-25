package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.ChangePasswordRequest;
import com.sosvietnam.model.payload.request.ChangePinRequest;
import com.sosvietnam.model.payload.request.PinLoginRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
import com.sosvietnam.model.payload.response.AccountInfoResponse;
import com.sosvietnam.model.payload.response.AuthenticationResponse;
import com.sosvietnam.model.payload.response.UnitMemberResponse;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.ProvinceRepository;
import com.sosvietnam.repository.WardRepository;
import com.sosvietnam.repository.RefreshTokenRepository;
import com.sosvietnam.repository.RoleRepository;
import com.sosvietnam.repository.UnitDutyAssignmentRepository;
import com.sosvietnam.repository.UnitMemberRepository;
import com.sosvietnam.security.CurrentOfficer;
import com.sosvietnam.security.OfficerPrincipal;
import com.sosvietnam.security.JwtTokenProvider;
import com.sosvietnam.security.StompSessionRegistry;
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
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * Xử lý xác thực: đăng nhập, cấp lại token, đăng ký công khai, lấy tài khoản hiện tại.
 *
 * Quy ước token (giống HIV-TMSS):
 * - JwtTokenProvider chỉ SINH token, không đụng DB.
 * - Service này là nơi DUY NHẤT lưu refresh token, với jitId = jwtId của access token
 *   đi kèm, để CustomLogoutHandler tìm được refresh token từ access token khi logout.
 *
 * Tài khoản đơn vị (OFFICER) đăng nhập 2 bước: email + mật khẩu đơn vị -> unit_token (token tạm),
 * rồi chọn cán bộ + PIN -> cặp token của cán bộ đó. ADMIN vào thẳng bằng cán bộ ẩn của tài khoản (không PIN).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenServiceImpl implements AuthenService {

    // Role mặc định cho tài khoản tự đăng ký qua API công khai
    private static final String PUBLIC_REGISTER_ROLE = RoleName.CITIZEN.getRole();

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final ProvinceRepository provinceRepository;
    private final WardRepository wardRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final StompSessionRegistry stompSessionRegistry;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final UnitMemberRepository unitMemberRepository;
    private final UnitDutyAssignmentRepository dutyRepository;
    private final CurrentOfficer currentOfficer;

    /** Sai PIN / mã kích hoạt tới số lần này thì khoá hồ sơ; trực ban / chỉ huy cấp lại PIN để mở. */
    static final int MAX_PIN_FAILURES = 5;

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

        // 3. Tài khoản đơn vị: chỉ cấp token tạm, cán bộ chọn mình + nhập PIN ở bước sau
        if (isRole(account, RoleName.OFFICER)) {
            return AuthenticationResponse.builder().unitToken(jwtTokenProvider.generateUnitToken(account)).build();
        }
        // ADMIN: cán bộ ẩn của tài khoản, không qua PIN
        if (isRole(account, RoleName.ADMIN)) {
            UnitMember member = unitMemberRepository.findByAccount_IdAndActiveTrueOrderByFullNameAsc(account.getId())
                    .stream().findFirst()
                    .orElseThrow(() -> new SosException(HttpStatus.INTERNAL_SERVER_ERROR, "Tài khoản quản trị chưa có hồ sơ cán bộ"));
            return issueMemberTokens(member);
        }

        // 4. Người dân: sinh cặp token và lưu refresh token (1 lần duy nhất)
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
        // Cấp trước lần đổi mật khẩu (đá văng mọi phiên): máy cũ không được xin token mới
        if (jwtTokenProvider.isIssuedBeforeLogoutAll(refreshToken)) {
            oldRefreshToken.setRevoked(true);
            oldRefreshToken.setExpired(true);
            refreshTokenRepository.save(oldRefreshToken);
            throw new SosException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã kết thúc, vui lòng đăng nhập lại");
        }

        Account account = accountRepository.findByEmail(userEmail)
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED,
                        "Invalid user. User not found"));

        // 3. Thu hồi cặp token cũ (access token cũ vào blacklist, refresh token cũ đánh dấu revoked)
        jwtTokenProvider.revokeAccessToken(account.getId(), oldRefreshToken.getJitId());
        oldRefreshToken.setRevoked(true);
        oldRefreshToken.setExpired(true);
        refreshTokenRepository.save(oldRefreshToken);

        // Token của cán bộ: cấp lại cho đúng cán bộ đó (còn hoạt động, chưa bị khoá)
        UUID memberId = jwtTokenProvider.memberIdOf(refreshToken);
        if (memberId != null) {
            UnitMember member = unitMemberRepository.findById(memberId)
                    .filter(m -> m.isActive() && !m.isLocked() && m.getAccount().getId().equals(account.getId()))
                    .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Phiên đăng nhập đã kết thúc, vui lòng đăng nhập lại"));
            return issueMemberTokens(member);
        }

        UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

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
                .province(request.getProvinceId() == null ? null
                        : provinceRepository.findById(request.getProvinceId())
                        .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Mã tỉnh/thành phố không hợp lệ")))
                .ward(request.getWardId() == null ? null
                        : wardRepository.findById(request.getWardId())
                        .filter(w -> w.getProvince().getCode().equals(request.getProvinceId()))
                        .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Xã/phường không thuộc tỉnh đã chọn")))
                .isActive(true)
                .isLocked(false)
                .build();

        Account saved = accountRepository.save(account);
        log.info("[USER REGISTERED] Account created: {} with Role: {}", saved.getEmail(), role.getRoleName());
    }

    @Override
    @Transactional(readOnly = true)
    public AccountInfoResponse getCurrentAccount() {
        // SecurityContext đã được JwtAuthenticationFilter gắn từ access token
        Account account = signedInAccount();
        AccountInfoResponse response = AccountInfoResponse.from(account);
        OfficerPrincipal officer = currentOfficer.orNull();
        if (officer != null && isRole(account, RoleName.OFFICER)) {
            unitMemberRepository.findById(officer.memberId())
                    .ifPresent(m -> response.setMember(UnitMemberResponse.from(m, isDutyOfficer(m))));
        }
        return response;
    }

    @Override
    public void changePassword(ChangePasswordRequest request) {
        Account account = signedInAccount();
        OfficerPrincipal officer = currentOfficer.orNull();
        // Mật khẩu đơn vị dùng chung: chỉ chỉ huy được đổi (đổi xong mọi cán bộ của đơn vị phải đăng nhập lại)
        if (officer != null && isRole(account, RoleName.OFFICER) && !officer.isCommander()) {
            throw new SosException(HttpStatus.FORBIDDEN, "Chỉ chỉ huy đơn vị được đổi mật khẩu tài khoản đơn vị");
        }
        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPassword())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Mật khẩu hiện tại không đúng");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPassword())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Mật khẩu mới phải khác mật khẩu hiện tại");
        }
        account.setPassword(passwordEncoder.encode(request.getNewPassword()));
        accountRepository.save(account);
        // Như HIV-TMSS: đổi mật khẩu thì mọi phiên đang đăng nhập (mọi thiết bị, kể cả máy này) phải đăng nhập lại
        jwtTokenProvider.disableOldTokens(account.getId());
        // màn hình trực ban đang mở trên máy khác mất kết nối ngay, kết nối lại thì bị chặn ở CONNECT
        UUID accountId = account.getId();
        int closed = stompSessionRegistry.closeWhere(p -> p instanceof OfficerPrincipal o && o.unitAccountId().equals(accountId));
        log.info("[CHANGE PASSWORD] {} (đã đá văng mọi phiên, đóng {} kết nối STOMP)", account.getEmail(), closed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<UnitMemberResponse> getUnitMembers(String unitToken) {
        Account account = unitAccount(unitToken);
        return unitMemberRepository.findByAccount_IdAndActiveTrueOrderByFullNameAsc(account.getId()).stream()
                .sorted(Comparator.comparing((UnitMember m) -> m.getUnitRole() != com.sosvietnam.model.payload.enums.UnitRole.COMMANDER))
                .map(UnitMemberResponse::forLogin)
                .toList();
    }

    @Override
    @Transactional(noRollbackFor = SosException.class) // sai PIN vẫn phải lưu số lần sai
    public AuthenticationResponse loginMember(String unitToken, PinLoginRequest request, boolean activate) {
        Account account = unitAccount(unitToken);
        UnitMember member = unitMemberRepository.findById(request.getMemberId())
                .filter(m -> m.isActive() && m.getAccount().getId().equals(account.getId()))
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Không tìm thấy cán bộ trong đơn vị"));
        if (member.isLocked()) {
            throw new SosException(HttpStatus.LOCKED,
                    "Hồ sơ đã bị khoá do nhập sai nhiều lần, liên hệ trực ban hoặc chỉ huy để cấp lại PIN");
        }
        if (activate) {
            boolean valid = member.getActivationCodeHash() != null && StringUtils.hasText(request.getActivationCode())
                    && member.getActivationExpiresAt() != null && member.getActivationExpiresAt().isAfter(LocalDateTime.now())
                    && passwordEncoder.matches(request.getActivationCode().trim().toUpperCase(Locale.ROOT),
                    member.getActivationCodeHash());
            if (!valid) throw failed(member, "Mã kích hoạt không đúng hoặc đã hết hạn");
            member.setPinHash(passwordEncoder.encode(request.getPin()));
            member.setActivationCodeHash(null);
            member.setActivationExpiresAt(null);
        } else {
            if (!member.isActivated()) {
                throw new SosException(HttpStatus.CONFLICT, "Cán bộ chưa kích hoạt, nhập mã kích hoạt để đặt PIN");
            }
            if (!passwordEncoder.matches(request.getPin(), member.getPinHash())) throw failed(member, "Sai mã PIN");
        }
        member.setFailedPinCount(0);
        unitMemberRepository.save(member);
        log.info("[PIN LOGIN] {} vào trực: {}{}", account.getEmail(), member.getFullName(), activate ? " (kích hoạt)" : "");
        return issueMemberTokens(member);
    }

    @Override
    public void changePin(ChangePinRequest request) {
        OfficerPrincipal me = currentOfficer.require();
        UnitMember member = unitMemberRepository.findById(me.memberId())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Không tìm thấy cán bộ"));
        if (!member.isActivated() || !passwordEncoder.matches(request.getOldPin(), member.getPinHash())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "PIN hiện tại không đúng");
        }
        if (request.getNewPin().equals(request.getOldPin())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "PIN mới phải khác PIN hiện tại");
        }
        member.setPinHash(passwordEncoder.encode(request.getNewPin()));
        unitMemberRepository.save(member);
        jwtTokenProvider.disableOldTokens(member.getId());
        stompSessionRegistry.closeAll(member.getId().toString());
        log.info("[CHANGE PIN] {} ({})", member.getFullName(), me.email());
    }

    /** Sai PIN / mã: tăng đếm, tới MAX_PIN_FAILURES thì khoá hồ sơ. */
    private SosException failed(UnitMember member, String message) {
        int failures = member.getFailedPinCount() + 1;
        member.setFailedPinCount(failures);
        if (failures >= MAX_PIN_FAILURES) {
            member.setLockedAt(LocalDateTime.now());
            unitMemberRepository.save(member);
            log.warn("[PIN LOCKED] {} của {}", member.getFullName(), member.getAccount().getEmail());
            return new SosException(HttpStatus.LOCKED,
                    "Nhập sai " + MAX_PIN_FAILURES + " lần, hồ sơ đã bị khoá. Liên hệ trực ban hoặc chỉ huy để cấp lại PIN");
        }
        unitMemberRepository.save(member);
        return new SosException(HttpStatus.UNAUTHORIZED, message + " (còn " + (MAX_PIN_FAILURES - failures) + " lần thử)");
    }

    /** Tài khoản đơn vị của token tạm (header "Bearer ..."): hết hạn / sai -> 401 để FE quay lại bước 1. */
    private Account unitAccount(String header) {
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
        UUID accountId = jwtTokenProvider.unitAccountIdFrom(token);
        return (accountId == null ? Optional.<Account>empty() : accountRepository.findById(accountId))
                .filter(a -> a.isActive() && !a.isLocked())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Phiên chọn cán bộ đã hết hạn, vui lòng đăng nhập lại"));
    }

    private AuthenticationResponse issueMemberTokens(UnitMember member) {
        String accessToken = jwtTokenProvider.generateMemberAccessToken(member);
        String refreshToken = jwtTokenProvider.generateMemberRefreshToken(member);
        saveRefreshToken(accessToken, refreshToken);
        return AuthenticationResponse.builder().accessToken(accessToken).refreshToken(refreshToken).build();
    }

    private boolean isDutyOfficer(UnitMember member) {
        return dutyRepository.findFirstByAccount_IdAndEndedAtIsNull(member.getAccount().getId())
                .map(d -> d.getMember().getId().equals(member.getId()))
                .orElse(false);
    }

    /** Tài khoản đang đăng nhập (cán bộ: tài khoản đơn vị của cán bộ; người dân: theo email). */
    private Account signedInAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        OfficerPrincipal officer = currentOfficer.orNull();
        return (officer != null ? accountRepository.findById(officer.unitAccountId())
                : accountRepository.findByEmail(authentication.getName()))
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Không tìm thấy tài khoản"));
    }

    private static boolean isRole(Account account, RoleName role) {
        return account.getRole() != null && role.getRole().equals(account.getRole().getRoleName());
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
