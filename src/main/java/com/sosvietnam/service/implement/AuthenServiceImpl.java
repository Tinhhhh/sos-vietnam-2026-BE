package com.sosvietnam.service.implement;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.payload.exception.SosException;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class AuthenServiceImpl implements AuthenService {

    private final AuthenticationManager authenticationManager;
    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    public AuthenticationResponse authenticate(AuthenticationRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        Account account = accountRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new SosException(HttpStatus.UNAUTHORIZED, "Authentication failed, account not found"));

        if (account.isLocked()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "Tai khoan da bi khoa, vui long lien he quan tri vien");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        saveRefreshToken(accessToken, refreshToken);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accountId(account.getId())
                .email(account.getEmail())
                .fullName(account.fullName())
                .role(account.getRole() != null ? account.getRole().getRoleName() : "CITIZEN")
                .agencyType(account.getAgencyType() != null ? account.getAgencyType().name() : null)
                .province(account.getProvince())
                .build();
    }

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

    @Override
    public AuthenticationResponse refreshToken(HttpServletRequest request, HttpServletResponse response) {
        final String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "No JWT token found in request header");
        }

        final String refreshToken = authHeader.substring(7);
        final String userEmail = jwtTokenProvider.getUsernameFromJwt(refreshToken);

        if (userEmail != null) {
            UserDetails userDetails = userDetailsService.loadUserByUsername(userEmail);

            RefreshToken oldRefreshToken = refreshTokenRepository.findByToken(refreshToken)
                    .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Token is invalid or does not exist"));

            if (!oldRefreshToken.isRevoked() && !oldRefreshToken.isExpired()) {
                Authentication authentication = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

                Account account = accountRepository.findByEmail(userEmail)
                        .orElseThrow(() -> new SosException(HttpStatus.BAD_REQUEST, "Invalid user. User not found"));

                // Revoke old tokens
                jwtTokenProvider.revokeAccessToken(account.getId(), oldRefreshToken.getJitId());

                oldRefreshToken.setRevoked(true);
                oldRefreshToken.setExpired(true);
                refreshTokenRepository.save(oldRefreshToken);

                // Generate new token pair
                String newAccessToken = jwtTokenProvider.generateAccessToken(authentication);
                String newRefreshToken = jwtTokenProvider.generateRefreshToken(authentication);

                saveRefreshToken(newAccessToken, newRefreshToken);

                return AuthenticationResponse.builder()
                        .accessToken(newAccessToken)
                        .refreshToken(newRefreshToken)
                        .tokenType("Bearer")
                        .accountId(account.getId())
                        .email(account.getEmail())
                        .fullName(account.fullName())
                        .role(account.getRole() != null ? account.getRole().getRoleName() : "CITIZEN")
                        .agencyType(account.getAgencyType() != null ? account.getAgencyType().name() : null)
                        .province(account.getProvince())
                        .build();
            } else {
                throw new SosException(HttpStatus.BAD_REQUEST, "Token is invalid or expired");
            }
        }
        return null;
    }

    @Transactional
    @Override
    public AuthenticationResponse register(RegistrationRequest request) {
        if (accountRepository.existsByEmail(request.getEmail())) {
            throw new SosException(HttpStatus.BAD_REQUEST, "Email da ton tai: " + request.getEmail());
        }

        String targetRoleName = (request.getRoleName() != null && !request.getRoleName().isBlank())
                ? request.getRoleName().toUpperCase()
                : "CITIZEN";

        Role role = roleRepository.findByRoleName(targetRoleName)
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .roleName(targetRoleName)
                        .description("Role " + targetRoleName)
                        .build()));

        Account account = Account.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .phone(request.getPhone())
                .address(request.getAddress())
                .role(role)
                .agencyType(request.getAgencyType())
                .province(request.getProvince())
                .ward(request.getWard())
                .isActive(true)
                .isLocked(false)
                .build();

        Account saved = accountRepository.save(account);
        log.info("[USER REGISTERED] Account created: {} with Role: {}", saved.getEmail(), role.getRoleName());

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        String accessToken = jwtTokenProvider.generateAccessToken(authentication);
        String refreshToken = jwtTokenProvider.generateRefreshToken(authentication);
        saveRefreshToken(accessToken, refreshToken);

        return AuthenticationResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .accountId(saved.getId())
                .email(saved.getEmail())
                .fullName(saved.fullName())
                .role(role.getRoleName())
                .agencyType(saved.getAgencyType() != null ? saved.getAgencyType().name() : null)
                .province(saved.getProvince())
                .build();
    }

    @Override
    public Account getCurrentAccount() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new SosException(HttpStatus.UNAUTHORIZED, "User is not authenticated");
        }
        return accountRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new SosException(HttpStatus.NOT_FOUND, "Account not found"));
    }
}