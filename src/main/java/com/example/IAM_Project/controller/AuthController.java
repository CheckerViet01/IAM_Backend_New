package com.example.IAM_Project.controller;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final com.example.IAM_Project.service.KeycloakAdminService keycloakAdminService;

    @GetMapping("/keycloak-config")
    public ResponseEntity<ApiResponse<KeycloakConfigResponse>> getKeycloakConfig() {
        KeycloakConfigResponse config = KeycloakConfigResponse.builder()
                .enabled(keycloakAdminService.isKeycloakEnabled())
                .serverUrl("http://localhost:8180")
                .realm("iam-realm")
                .clientId("iam-frontend")
                .loginUrl(keycloakAdminService.getLoginUrl())
                .build();
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin cấu hình Keycloak thành công", config));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<String>> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = authService.register(request, httpRequest);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<AuthResponse> response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-2fa")
    public ResponseEntity<ApiResponse<AuthResponse>> verify2fa(
            @Valid @RequestBody VerifyOtpRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<AuthResponse> response = authService.verify2fa(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    public ResponseEntity<ApiResponse<AuthResponse>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<AuthResponse> response = authService.refreshToken(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<String>> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestParam(value = "refreshToken", required = false) String refreshToken,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = authService.logout(authHeader, refreshToken, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = authService.forgotPassword(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<String>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = authService.resetPassword(request, httpRequest);
        return ResponseEntity.ok(response);
    }
}
