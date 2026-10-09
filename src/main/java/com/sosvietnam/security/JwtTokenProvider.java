package com.sosvietnam.security;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.RefreshToken;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.repository.AccountRepository;
import com.sosvietnam.repository.RefreshTokenRepository;
import com.sosvietnam.repository.RoleRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;

    // In-memory blacklist for revoked tokens (userId_jwtId -> expiryTimestamp)
    private final Map<String, Long> tokenBlacklist = new ConcurrentHashMap<>();

    // Public sample key from online JWT tutorials (this repo shipped with it); anyone can sign tokens with it.
    private static final String PUBLIC_SAMPLE_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret-key:}")
    private String jwtSecret;

    private SecretKey signingKey;

    @Value("${app.jwt-access-expiration-milliseconds:${app.jwt.expiration-ms:86400000}}")
    private long jwtAccessExpiration;

    @Value("${app.jwt-refresh-expiration-milliseconds:${app.jwt.refresh-expiration-ms:604800000}}")
    private long jwtRefreshExpiration;

    /** Fails at startup instead of silently signing tokens with a weak or well-known secret. */
    @PostConstruct
    void initSigningKey() {
        String hint = " Put a random Base64 value in JWT_SECRET in the .env file (generate one with: openssl rand -base64 48).";
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("JWT_SECRET is empty." + hint);
        }
        if (PUBLIC_SAMPLE_KEY.equalsIgnoreCase(jwtSecret.trim())) {
            throw new IllegalStateException("JWT_SECRET is the public sample key from online tutorials, so anyone could forge tokens." + hint);
        }
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtSecret.trim());
        } catch (RuntimeException e) {
            throw new IllegalStateException("JWT_SECRET is not valid Base64." + hint);
        }
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT_SECRET is too short (" + keyBytes.length + " bytes, need " + MIN_SECRET_BYTES + ")." + hint);
        }
        signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    private SecretKey getSigningKey() {
        return signingKey;
    }

    public String generateAccessToken(Authentication authentication) {
        return generateToken(authentication);
    }

    public String generateToken(Authentication authentication) {
        String username = authentication.getName();
        Date currentDate = new Date();
        Date expirationDate = new Date(currentDate.getTime() + jwtAccessExpiration);

        Account account = accountRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("Account not found with email: " + username));

        String roleName = account.getRole() != null ? account.getRole().getRoleName() : "CITIZEN";

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(currentDate)
                .expiration(expirationDate)
                .claim("type", "access")
                .claim("id", account.getId().toString())
                .claim("role", roleName)
                .claim("fullName", account.fullName())
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(Authentication authentication) {
        return generateRefreshToken(authentication, jwtRefreshExpiration);
    }

    public String generateRefreshToken(Authentication authentication, long expiration) {
        String username = authentication.getName();
        Date currentDate = new Date();
        Date expirationDate = new Date(currentDate.getTime() + (expiration > 0 ? expiration : jwtRefreshExpiration));

        Account account = accountRepository.findByEmail(username)
                .orElseThrow(() -> new RuntimeException("Account not found with email: " + username));

        UUID jitId = UUID.randomUUID();
        String token = Jwts.builder()
                .id(jitId.toString())
                .subject(username)
                .issuedAt(currentDate)
                .expiration(expirationDate)
                .claim("type", "refresh")
                .claim("id", account.getId().toString())
                .signWith(getSigningKey())
                .compact();

        RefreshToken refreshToken = RefreshToken.builder()
                .token(token)
                .jitId(jitId)
                .expired(false)
                .revoked(false)
                .build();
        refreshTokenRepository.save(refreshToken);

        return token;
    }

    public String generateTokenFromEmail(String email, String role) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtAccessExpiration);

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(email)
                .claim("roles", role)
                .claim("role", role)
                .claim("type", "access")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromJwt(String jwt) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(jwt)
                .getPayload();
        return claims.getSubject();
    }

    public AccessToken getTokenFromJwt(String jwt) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(jwt)
                .getPayload();

        String roleName = claims.get("role", String.class);
        Role role = roleName != null ? roleRepository.findByRoleName(roleName).orElse(null) : null;

        return AccessToken.builder()
                .jwtId(claims.getId() != null ? UUID.fromString(claims.getId()) : null)
                .userId(claims.get("id") != null ? UUID.fromString(claims.get("id").toString()) : null)
                .fullName(claims.get("fullName", String.class))
                .email(claims.getSubject())
                .roleId(role != null ? role.getId() : null)
                .issuedAt(claims.getIssuedAt())
                .expiresAt(claims.getExpiration())
                .build();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            return false;
        }
    }

    public boolean isTokenValid(String jwt, String username) {
        String tokenUserName = getUsernameFromJwt(jwt);
        return tokenUserName.equals(username) && !isTokenExpired(jwt);
    }

    public boolean isTokenExpired(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (Exception e) {
            return true;
        }
    }

    public boolean isRefreshToken(String jwt) {
        if (!StringUtils.hasText(jwt)) return false;
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            String type = claims.get("type", String.class);
            return "refresh".equalsIgnoreCase(type);
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isTokenRevoked(UUID userId, UUID jwtId) {
        if (userId == null || jwtId == null) return false;
        String key = userId + "_" + jwtId;
        Long expiry = tokenBlacklist.get(key);
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            tokenBlacklist.remove(key);
            return false;
        }
        return true;
    }

    public void revokeAccessToken(UUID userId, UUID jwtId) {
        if (userId == null || jwtId == null) return;
        String key = userId + "_" + jwtId;
        tokenBlacklist.put(key, System.currentTimeMillis() + jwtAccessExpiration);
    }

    public String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    public Account getAccountFromRequest(HttpServletRequest request) {
        String token = getJwtFromRequest(request);
        if (!StringUtils.hasText(token)) throw new RuntimeException("No JWT token found in request");
        String email = getUsernameFromJwt(token);
        return accountRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Account not found: " + email));
    }
}