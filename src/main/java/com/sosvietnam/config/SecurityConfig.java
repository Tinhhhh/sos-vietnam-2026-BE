package com.sosvietnam.config;

import com.sosvietnam.security.CustomLogoutHandler;
import com.sosvietnam.security.JwtAuthenticationEntryPoint;
import com.sosvietnam.security.JwtAuthenticationFilter;
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

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UserDetailsService userDetailsService;
    private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint;
    private final JwtAuthenticationFilter authenticationFilter;
    private final CustomLogoutHandler logoutHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .cors(withDefaults())
                .authorizeHttpRequests(request ->
                        request.requestMatchers(
                                        "/swagger-ui/**",
                                        "/v3/api-docs/**",
                                        "/api/v1/auth/**",
                                        "/api/geo/**",
                                        "/api/stations/**",
                                        "/api/incidents/**",
                                        "/ws/**",
                                        "/ws-raw/**",
                                        "/error").permitAll()
                                .requestMatchers(HttpMethod.GET, "/api/v1/accounts").hasAnyAuthority("ADMIN", "DISPATCHER", "OFFICER", "CITIZEN")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/accounts").hasAnyAuthority("ADMIN", "DISPATCHER", "OFFICER", "CITIZEN")
                                .requestMatchers("/api/v1/accounts/admin/**").hasAuthority("ADMIN")
                                .requestMatchers("/api/v1/admin/**").hasAuthority("ADMIN")
                                .anyRequest().authenticated()
                )
                .addFilterBefore(authenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(e -> e.authenticationEntryPoint(jwtAuthenticationEntryPoint));

        // Note: Logout is handled via AuthController POST /api/v1/auth/logout using CustomLogoutHandler identical to HIV-TMSS
        return http.build();
    }
}