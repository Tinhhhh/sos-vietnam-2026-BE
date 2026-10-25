package com.sosvietnam.security;

import com.sosvietnam.model.entity.AccessToken;
import com.sosvietnam.model.entity.Account;
import com.sosvietnam.model.entity.Role;
import com.sosvietnam.model.entity.UnitMember;
import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.repository.AccountRepository;
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
import java.util.UUID;

/**
 * Sinh và đọc JWT. Không ghi DB. Token đã thu hồi và mốc "đá văng mọi phiên" nằm ở TokenRevocationStore (RAM + Redis).
 * Loại token (claim "type"):
 * - "unit": token tạm sau khi đăng nhập email + mật khẩu đơn vị (vài phút), chỉ dùng để chọn cán bộ + nhập PIN.
 * - "access" / "refresh": của một cán bộ, claim "id" = tài khoản đơn vị, "mid" = cán bộ (unit_members).
 *   Tài khoản không có cán bộ (người dân) thì không có "mid".
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final AccountRepository accountRepository;
    private final RoleRepository roleRepository;
    private final TokenRevocationStore revocations;

    // Public sample key from online JWT tutorials (this repo shipped with it); anyone can sign tokens with it.
    private static final String PUBLIC_SAMPLE_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int MIN_SECRET_BYTES = 32;

    @Value("${app.jwt.secret-key:}")
    private String jwtSecret;

    private SecretKey signingKey;

    @Value("${app.jwt-access-expiration-milliseconds:${app.jwt.expiration-ms:86400000}}")
    private long jwtAccessExpiration;

    @Value("${app.jwt.unit-expiration-ms:300000}")
    private long jwtUnitExpiration;

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

        String roleName = account.getRole() != null ? account.getRole().getRoleName() : RoleName.CITIZEN.getRole();

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

        // Chỉ sinh token. Việc lưu refresh token vào DB do AuthenServiceImpl.saveRefreshToken() làm
        // (trước đây lưu ở cả 2 nơi -> 2 dòng trùng token -> findByToken() lỗi 500 khi refresh).
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(username)
                .issuedAt(currentDate)
                .expiration(expirationDate)
                .claim("type", "refresh")
                .claim("id", account.getId().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** Access token của một cán bộ (sau khi nhập PIN; admin: cán bộ ẩn của tài khoản). */
    public String generateMemberAccessToken(UnitMember member) {
        return memberToken(member, "access", jwtAccessExpiration);
    }

    public String generateMemberRefreshToken(UnitMember member) {
        return memberToken(member, "refresh", jwtRefreshExpiration);
    }

    private String memberToken(UnitMember member, String type, long expiration) {
        Account account = member.getAccount();
        Date now = new Date();
        JwtBuilder builder = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(account.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
                .claim("type", type)
                .claim("id", account.getId().toString())
                .claim("mid", member.getId().toString());
        if ("access".equals(type)) {
            builder.claim("role", account.getRole() == null ? RoleName.CITIZEN.getRole() : account.getRole().getRoleName())
                    .claim("fullName", member.displayName());
        }
        return builder.signWith(getSigningKey()).compact();
    }

    /** Token tạm của tài khoản đơn vị: chỉ để gọi /auth/unit/members, /auth/unit/pin, /auth/unit/activate. */
    public String generateUnitToken(Account account) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(account.getEmail())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + jwtUnitExpiration))
                .claim("type", "unit")
                .claim("id", account.getId().toString())
                .signWith(getSigningKey())
                .compact();
    }

    /** id tài khoản đơn vị trong token tạm còn hạn, chưa bị đá văng; null nếu không phải token tạm hợp lệ. */
    public UUID unitAccountIdFrom(String jwt) {
        Claims claims = claimsOrNull(jwt);
        if (claims == null || !"unit".equals(claims.get("type", String.class)) || claims.get("id") == null) return null;
        UUID accountId = UUID.fromString(claims.get("id").toString());
        return revocations.issuedBeforeLogoutAll(accountId, claims.getIssuedAt()) ? null : accountId;
    }

    /** Loại token ("access" / "refresh" / "unit"), null nếu token hỏng hoặc hết hạn. */
    public String tokenType(String jwt) {
        Claims claims = claimsOrNull(jwt);
        return claims == null ? null : claims.get("type", String.class);
    }

    /** Cán bộ của token (claim "mid"), null nếu token không gắn cán bộ. */
    public UUID memberIdOf(String jwt) {
        Claims claims = claimsOrNull(jwt);
        Object mid = claims == null ? null : claims.get("mid");
        return mid == null ? null : UUID.fromString(mid.toString());
    }

    private Claims claimsOrNull(String jwt) {
        if (!StringUtils.hasText(jwt)) return null;
        try {
            return Jwts.parser().verifyWith(getSigningKey()).build().parseSignedClaims(jwt).getPayload();
        } catch (Exception e) {
            return null;
        }
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
        Role role = RoleName.isExistRole(roleName) ? roleRepository.findByRoleName(roleName).orElse(null) : null;

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
        return tokenUserName.equals(username) && !isTokenExpired(jwt) && !isIssuedBeforeLogoutAll(jwt);
    }

    /**
     * Token (access hoặc refresh) cấp trước lần "đá văng mọi phiên" gần nhất của tài khoản (đổi mật khẩu)?
     * Như TOKEN_IAT_AVAILABLE của HIV-TMSS, áp cho cả refresh token để máy cũ không xin được token mới.
     */
    public boolean isIssuedBeforeLogoutAll(String jwt) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(jwt)
                    .getPayload();
            // mốc của tài khoản đơn vị (đổi mật khẩu đơn vị: mọi cán bộ) hoặc của riêng cán bộ (đổi PIN, bị khoá, bị xoá)
            Object id = claims.get("id");
            Object mid = claims.get("mid");
            return (id != null && revocations.issuedBeforeLogoutAll(UUID.fromString(id.toString()), claims.getIssuedAt()))
                    || (mid != null && revocations.issuedBeforeLogoutAll(UUID.fromString(mid.toString()), claims.getIssuedAt()));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Đá văng mọi phiên đang đăng nhập (mọi thiết bị). id = tài khoản đơn vị (mọi cán bộ của đơn vị) hoặc
     * id cán bộ (chỉ người đó). Nhớ đủ lâu để chặn cả refresh token.
     */
    public void disableOldTokens(UUID accountOrMemberId) {
        revocations.logoutEverywhere(accountOrMemberId, Math.max(jwtAccessExpiration, jwtRefreshExpiration));
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
        return revocations.isRevoked(userId, jwtId);
    }

    public void revokeAccessToken(UUID userId, UUID jwtId) {
        revocations.revoke(userId, jwtId, jwtAccessExpiration);
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