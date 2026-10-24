package com.sosvietnam.controller;

import com.sosvietnam.model.payload.exception.ResponseBuilder;
import com.sosvietnam.model.payload.request.AuthenticationRequest;
import com.sosvietnam.model.payload.request.ChangePasswordRequest;
import com.sosvietnam.model.payload.request.ChangePinRequest;
import com.sosvietnam.model.payload.request.PinLoginRequest;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

/** API xác thực: đăng nhập, refresh token, đăng xuất, đăng ký công khai (CITIZEN), thông tin tài khoản hiện tại. */
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

    @Operation(summary = "Login to the system", description = "Login with email + password. Role required: none")
    @PostMapping("/login")
    @ResponseStatus(HttpStatus.OK)
    public ResponseEntity<Object> signIn(@RequestBody @Valid AuthenticationRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully Sign in", authService.authenticate(request));
    }

    @Operation(summary = "Refresh token if expired", description = "Send the refresh token as 'Authorization: Bearer <refresh_token>'. Role required: none")
    @PostMapping("/refresh-token")
    public ResponseEntity<Object> refreshToken(HttpServletRequest request, HttpServletResponse response) throws IOException {
        return ResponseBuilder.returnData(HttpStatus.OK, "Generate new Refresh Token and Access Token successfully", authService.refreshToken(request, response));
    }

    @Operation(summary = "Logout of the system", description = "Revokes the current access token and its refresh token. Role required: logged in")
    @PostMapping("/logout")
    public ResponseEntity<Object> logout(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        logoutHandler.logout(request, response, authentication);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Logged out successfully");
    }

    @Operation(summary = "Register a new citizen account", description = "Public sign-up always creates a CITIZEN account. Role required: none")
    @PostMapping("/register")
    public ResponseEntity<Object> register(@RequestBody @Valid RegistrationRequest request) {
        authService.register(request);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Your account is created successfully");
    }

    @Operation(summary = "Change my password", description = "current_password must match; new_password 8-100 characters. Logs out every session of the account (all devices). Role required: logged in")
    @PostMapping("/change-password")
    public ResponseEntity<Object> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        authService.changePassword(request);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Đã đổi mật khẩu, vui lòng đăng nhập lại trên mọi thiết bị");
    }

    @Operation(summary = "List the officers of my unit (login step 2)",
            description = "Header 'Authorization: Bearer <unit_token>' from /login of a unit account. Role required: none")
    @GetMapping("/unit/members")
    public ResponseEntity<Object> getUnitMembers(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String unitToken) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Unit officers", authService.getUnitMembers(unitToken));
    }

    @Operation(summary = "Officer enters the PIN (login step 2)",
            description = "Header 'Authorization: Bearer <unit_token>'. Wrong PIN 401 (5 times -> 423 locked); not activated 409. "
                    + "Returns the officer's access + refresh token. Role required: none")
    @PostMapping("/unit/pin")
    public ResponseEntity<Object> pinLogin(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String unitToken,
                                           @RequestBody @Valid PinLoginRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully Sign in", authService.loginMember(unitToken, request, false));
    }

    @Operation(summary = "Officer sets the first PIN with the activation code",
            description = "Header 'Authorization: Bearer <unit_token>', body { member_id, activation_code, pin }. Role required: none")
    @PostMapping("/unit/activate")
    public ResponseEntity<Object> activate(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String unitToken,
                                           @RequestBody @Valid PinLoginRequest request) {
        return ResponseBuilder.returnData(HttpStatus.OK, "Successfully Sign in", authService.loginMember(unitToken, request, true));
    }

    @Operation(summary = "Change my PIN", description = "Logs out every session of this officer only. Role required: OFFICER")
    @PostMapping("/pin")
    public ResponseEntity<Object> changePin(@RequestBody @Valid ChangePinRequest request) {
        authService.changePin(request);
        return ResponseBuilder.returnMessage(HttpStatus.OK, "Đã đổi PIN, vui lòng vào trực lại");
    }

    @Operation(summary = "Get current authenticated account details", description = "Role required: logged in")
    @GetMapping("/me")
    public ResponseEntity<Object> getCurrentUser() {
        return ResponseBuilder.returnData(HttpStatus.OK, "Current user account", authService.getCurrentAccount());
    }
}