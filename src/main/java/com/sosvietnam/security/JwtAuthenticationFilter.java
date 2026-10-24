package com.sosvietnam.security;

import com.sosvietnam.model.entity.AccessToken;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Gắn người dùng từ access token. Token của cán bộ (có claim "mid") -> principal = OfficerPrincipal;
 * token không gắn cán bộ (người dân) -> UserDetails như cũ. Refresh token và token tạm của đơn vị ("unit")
 * không đăng nhập được API nào (các API /auth/unit/* tự đọc token tạm).
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final OfficerPrincipalLoader officerPrincipalLoader;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String jwt = jwtTokenProvider.getJwtFromRequest(request);
        if (SecurityContextHolder.getContext().getAuthentication() == null
                && "access".equals(jwtTokenProvider.tokenType(jwt))) {
            authenticate(jwt, request);
        }
        filterChain.doFilter(request, response);
    }

    private void authenticate(String jwt, HttpServletRequest request) {
        AccessToken token = jwtTokenProvider.getTokenFromJwt(jwt);
        if (token == null || jwtTokenProvider.isTokenRevoked(token.getUserId(), token.getJwtId())
                || jwtTokenProvider.isIssuedBeforeLogoutAll(jwt)) {
            return;
        }
        UsernamePasswordAuthenticationToken authToken;
        UUID memberId = jwtTokenProvider.memberIdOf(jwt);
        if (memberId != null) {
            OfficerPrincipal officer = officerPrincipalLoader.load(memberId)
                    .filter(o -> o.unitAccountId().equals(token.getUserId()))
                    .orElse(null);
            if (officer == null) return;
            authToken = new UsernamePasswordAuthenticationToken(officer, null,
                    officer.role() == null ? List.of() : List.of(new SimpleGrantedAuthority(officer.role().getRole())));
        } else {
            UserDetails userDetails = userDetailsService.loadUserByUsername(token.getEmail());
            if (!jwtTokenProvider.isTokenValid(jwt, userDetails.getUsername())) return;
            authToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        }
        authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authToken);
    }
}
