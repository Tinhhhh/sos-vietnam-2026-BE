package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.RegistrationRequest;
import com.sosvietnam.security.CustomLogoutHandler;
import com.sosvietnam.service.AuthenService;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.security.SecuritySchemes;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@OpenAPIDefinition(info = @Info(
        title = "SOS Vietnam REST API", version = "1.0", description = "API documentation for SOS Vietnam",
        contact = @Contact(name = "Github", url = "https://github.com/Tinhhhh")),
        security = {@SecurityRequirement(name = "bearerToken")}
)
@SecuritySchemes({
        @SecurityScheme(name = "bearerToken", type = SecuritySchemeType.HTTP, scheme = "bearer", bearerFormat = "JWT")
})
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "Authentication and Token Management")
public class AuthController {

    private final AuthenService authService;
    private final CustomLogoutHandler logoutHandler;

    @Operation(summary = "Login to the system")
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> signIn(@RequestBody @Valid AuthenticationRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully Sign in", authService.authenticate(request));
    }

    @Operation(summary = "Refresh token if expired")
    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return ResponseBuilder.returnData(HttpStatus.OK, "Generate new Refresh Token and Access Token successfully", authService.refreshToken(request, response));
    }

    @Operation(summary = "Logout of the system")
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Logged out successfully");
    }

    @Operation(summary = "Register a new account")
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody @Valid RegistrationRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Your account is created successfully", authService.register(request));
    }

    @Operation(summary = "Get current authenticated account details")
    @GetMapping("/me")
    public ResponseEntity<Object> getCurrentUser() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Current user account", authService.getCurrentAccount());
    }
}