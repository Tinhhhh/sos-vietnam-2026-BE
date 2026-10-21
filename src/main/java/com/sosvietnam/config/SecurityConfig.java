package com.sosvietnam.config;

import com.sosvietnam.model.payload.enums.RoleName;
import com.sosvietnam.security.CustomLogoutHandler;
import com.sosvietnam.security.JwtAuthenticationEntryPoint;
import com.sosvietnam.security.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Phân quyền theo URL (kiểu HIV-TMSS). Authority = role_name trong bảng roles: ADMIN, OFFICER, CITIZEN.
 * JWT được đọc ở JwtAuthenticationFilter; thiếu / sai token -> JwtAuthenticationEntryPoint trả 401.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter authenticationFilter;
    private final CustomLogoutHandler logoutHandler;

    private static final String ADMIN = RoleName.ADMIN.getRole();
    private static final String[] STAFF = {RoleName.OFFICER.getRole(), RoleName.ADMIN.getRole()};

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .authorizeHttpRequests(request -> request
                        // ── Swagger, WebSocket, error ────────────────────────────────
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**",
                                "/ws/**", "/ws-raw/**", "/error").permitAll()

                        // ── Auth: /me cần đăng nhập, còn lại công khai ──────────────
                        // (phải khai báo /me TRƯỚC "/api/v1/auth/**", rule nào khớp trước thắng)
                        .requestMatchers("/api/v1/auth/me", "/api/v1/auth/change-password").authenticated()
                        .requestMatchers("/api/v1/auth/**").permitAll()

                        // ── Geo / Station: tra cứu công khai, đồng bộ ranh giới chỉ ADMIN ─
                        .requestMatchers("/api/v1/geo/sync-status", "/api/v1/geo/sync-now").hasAuthority(ADMIN)
                        // phiên bản dữ liệu + thời tiết tại đơn vị: chỉ màn hình trực ban dùng
                        .requestMatchers(HttpMethod.GET, "/api/v1/geo/data-status", "/api/v1/weather/**")
                                .hasAnyAuthority(STAFF)
                        .requestMatchers(HttpMethod.GET, "/api/v1/geo/**", "/api/v1/stations", "/api/v1/stations/**").permitAll()

                        // ── Incident: người dân gửi SOS + ảnh không cần đăng nhập ─────
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents", "/api/v1/incidents/lookup", "/api/v1/incidents/*/media", "/api/v1/incidents/*/cancel",
                                // escalate: cán bộ (JWT) hoặc người dân (citizen token), service tự kiểm
                                "/api/v1/incidents/*/escalate").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/*/media/*", "/api/v1/incidents/*/tracking").permitAll()
                        // người dân xem / ký văn bản báo cáo bằng citizen token hoặc mã tra cứu
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/lookup/document",
                                "/api/v1/incidents/lookup/document/sign", "/api/v1/incidents/*/document/citizen-sign").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/*/document/citizen").permitAll()
                        // văn bản báo cáo: cán bộ lập, ký, ký hộ, tải Word
                        .requestMatchers("/api/v1/incidents/*/document", "/api/v1/incidents/*/document/**").hasAnyAuthority(STAFF)
                        // kế hoạch điều động: cán bộ (JWT) hoặc người báo tin (citizen token), DispatchServiceImpl tự kiểm
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/*/dispatch-plan").permitAll()
                        .requestMatchers(HttpMethod.PUT, "/api/v1/incidents/*/dispatch-plan").hasAnyAuthority(STAFF)
                        // xác nhận đã xử lý xong: cán bộ (JWT) hoặc người dân (citizen token), service tự kiểm
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/*/dispatch-plan/confirm").permitAll()
                        .requestMatchers("/api/v1/incidents/*/dispatch-plan/**").hasAnyAuthority(STAFF)
                        // Cán bộ (JWT) hoặc người báo tin (citizen token): CallServiceImpl tự kiểm
                        .requestMatchers(HttpMethod.GET, "/api/v1/calls/ice-servers").permitAll()
                        // xem danh sách + phân công là việc của trực ban
                        .requestMatchers(HttpMethod.GET, "/api/v1/incidents/queue", "/api/v1/incidents/history", "/api/v1/incidents/*", "/api/v1/incidents/*/handover-candidates")
                                .hasAnyAuthority(STAFF)
                        // thống kê của cán bộ (phạm vi lấy từ tài khoản)
                        .requestMatchers(HttpMethod.GET, "/api/v1/dashboard/**", "/api/v1/officers/**").hasAnyAuthority(STAFF)
                        .requestMatchers(HttpMethod.POST, "/api/v1/incidents/*/dispatch", "/api/v1/incidents/*/claim",
                                "/api/v1/incidents/*/release", "/api/v1/incidents/*/request-dispatch",
                                "/api/v1/incidents/*/officer-cancel").hasAnyAuthority(STAFF)

                        // ── Quản trị ────────────────────────────────────────────────
                        .requestMatchers("/api/v1/admin/**").hasAuthority(ADMIN)
                        .anyRequest().authenticated()
                )
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
                        // Đã đăng nhập nhưng sai quyền -> 403 dạng JSON (mặc định Spring trả body rỗng)
                        .accessDeniedHandler((req, res, ex) -> {
                            res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            res.setContentType("application/json;charset=UTF-8");
                            res.getWriter().write("{\"http_status\": 403, \"message\": \"Tài khoản không có quyền thực hiện thao tác này\"}");
                        }));

        // Note: Logout is handled via AuthController POST /api/v1/auth/logout using CustomLogoutHandler identical to HIV-TMSS
        return http.build();
    }
}