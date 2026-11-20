package com.example.IAM_Project.service;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.entity.RefreshToken;
import com.example.IAM_Project.entity.Role;
import com.example.IAM_Project.entity.TokenBlacklist;
import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.VerificationToken;
import com.example.IAM_Project.exception.BadRequestException;
import com.example.IAM_Project.exception.ResourceNotFoundException;
import com.example.IAM_Project.exception.UnauthorizedException;
import com.example.IAM_Project.repository.RefreshTokenRepository;
import com.example.IAM_Project.repository.RoleRepository;
import com.example.IAM_Project.repository.TokenBlacklistRepository;
import com.example.IAM_Project.repository.UserRepository;
import com.example.IAM_Project.repository.VerificationTokenRepository;
import com.example.IAM_Project.security.JwtUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenBlacklistRepository tokenBlacklistRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final RedisService redisService;
    private final MailService mailService;
    private final ActivityLogService activityLogService;
    private final KeycloakAdminService keycloakAdminService;

    private static final SecureRandom RANDOM = new SecureRandom();

    public String getClientIp(HttpServletRequest request) {
        if (request == null) return "127.0.0.1";
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }

    public String generateNumericOtp(int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) {
            sb.append(RANDOM.nextInt(10));
        }
        return sb.toString();
    }

    @Transactional
    public ApiResponse<String> register(RegisterRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            activityLogService.log(null, email, "REGISTER", "FAILED", httpRequest, "Email đã tồn tại");
            throw new BadRequestException("Email đã được đăng ký trong hệ thống!");
        }

        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò ROLE_USER trong hệ thống"));

        Set<Role> roles = new HashSet<>();
        roles.add(userRole);

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .roles(roles)
                .isActive(true)
                .isLocked(false)
                .failedLoginAttempts(0)
                .isEmailVerified(true)
                .twoFactorEnabled(false)
                .twoFactorMethod("EMAIL")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        user = userRepository.save(user);

        // Đồng bộ tạo tài khoản trên Keycloak nếu bật Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            keycloakAdminService.createKeycloakUser(email, request.getPassword(), request.getFullName(), true);
        }

        // Gửi email chào mừng xác nhận tạo tài khoản
        try {
            mailService.sendRegistrationConfirmation(user);
        } catch (Exception ignored) {}

        activityLogService.log(user, user.getEmail(), "REGISTER", "SUCCESS", httpRequest, "Đăng ký tài khoản thành công");

        return ApiResponse.ok("Đăng ký tài khoản thành công! Bạn có thể đăng nhập ngay.");
    }

    @Transactional
    public ApiResponse<AuthResponse> login(LoginRequest request, HttpServletRequest httpRequest) {
        // 1. Kiểm tra nếu hệ thống đang bật Keycloak IDP
        if (keycloakAdminService.isKeycloakEnabled()) {
            String email = request.getEmail() != null ? request.getEmail().trim().toLowerCase() : "";
            
            // Đề bài yêu cầu: "mỗi request kiểm tra thông tin user trong database của iam, nếu database của iam có thì mới cho thực hiện request"
            User user = userRepository.findByEmail(email).orElse(null);

            if (user != null && request.getPassword() != null && !request.getPassword().isBlank()) {
                Map<String, Object> kcToken = keycloakAdminService.loginDirect(email, request.getPassword());
                if (kcToken != null && kcToken.containsKey("access_token")) {
                    String accessToken = (String) kcToken.get("access_token");
                    String refreshToken = (String) kcToken.get("refresh_token");
                    long expiresIn = ((Number) kcToken.getOrDefault("expires_in", 300)).longValue();

                    user.setLastLoginAt(LocalDateTime.now());
                    user.setLastLoginIp(getClientIp(httpRequest));
                    userRepository.save(user);

                    activityLogService.log(user, email, "LOGIN_KEYCLOAK", "SUCCESS", httpRequest, "Đăng nhập Keycloak thành công");

                    return ApiResponse.ok("Đăng nhập Keycloak thành công", AuthResponse.builder()
                            .accessToken(accessToken)
                            .refreshToken(refreshToken)
                            .tokenType("Bearer")
                            .expiresIn(expiresIn)
                            .requires2fa(false)
                            .loginUrl(keycloakAdminService.getLoginUrl())
                            .idpProvider("keycloak")
                            .user(mapToProfileResponse(user))
                            .build());
                }
            }

            // Theo đúng đề bài: "Khi người dùng gọi tới /login thì trả về thông báo yêu cầu người dùng truy cập trang đăng nhập USER của keycloak"
            return ApiResponse.ok("Hệ thống đang sử dụng Keycloak Identity Server. Vui lòng truy cập trang đăng nhập Keycloak để nhận Access Token.",
                    AuthResponse.builder()
                            .loginUrl(keycloakAdminService.getLoginUrl())
                            .idpProvider("keycloak")
                            .message("Yêu cầu truy cập trang đăng nhập USER của Keycloak để nhận access_token.")
                            .build());
        }

        // 2. Chế độ Self-IDP nội bộ (giữ nguyên cơ chế Service 1)
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> {
                    activityLogService.log(null, email, "LOGIN", "FAILED", httpRequest, "Tài khoản không tồn tại");
                    return new BadRequestException("Email hoặc mật khẩu không chính xác");
                });

        // Kiểm tra xem tài khoản có bị khóa không
        if (Boolean.TRUE.equals(user.getIsLocked())) {
            if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
                activityLogService.log(user, email, "LOGIN", "FAILED", httpRequest, "Tài khoản đang bị tạm khóa");
                throw new BadRequestException("Tài khoản đang tạm khóa do nhập sai mật khẩu quá 5 lần. Thử lại sau " + user.getLockedUntil());
            } else {
                user.setIsLocked(false);
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }
        }

        if (!Boolean.TRUE.equals(user.getIsActive())) {
            activityLogService.log(user, email, "LOGIN", "FAILED", httpRequest, "Tài khoản đã bị vô hiệu hóa");
            throw new BadRequestException("Tài khoản đã bị vô hiệu hóa bởi quản trị viên");
        }

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            int failedAttempts = user.getFailedLoginAttempts() != null ? user.getFailedLoginAttempts() + 1 : 1;
            user.setFailedLoginAttempts(failedAttempts);

            String detail = "Mật khẩu không đúng (Lần " + failedAttempts + ")";
            if (failedAttempts >= 5) {
                user.setIsLocked(true);
                user.setLockedUntil(LocalDateTime.now().plusMinutes(15));
                detail += " -> Khóa tài khoản 15 phút";
            }
            userRepository.save(user);
            activityLogService.log(user, email, "LOGIN", "FAILED", httpRequest, detail);

            throw new BadRequestException("Email hoặc mật khẩu không chính xác");
        }

        // Đăng nhập thành công -> Reset failed attempts
        user.setFailedLoginAttempts(0);
        user.setIsLocked(false);
        user.setLockedUntil(null);

        // Kiểm tra xác thực 2 bước (2FA)
        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            String otp = generateNumericOtp(6);
            redisService.storeOtp(email, otp, 10);

            VerificationToken verificationToken = VerificationToken.builder()
                    .user(user)
                    .tokenCode(otp)
                    .tokenType("TWO_FACTOR_OTP")
                    .deliveryChannel("EMAIL")
                    .destination(email)
                    .attempts(0)
                    .maxAttempts(5)
                    .expiresAt(LocalDateTime.now().plusMinutes(10))
                    .isUsed(false)
                    .createdAt(LocalDateTime.now())
                    .build();
            verificationTokenRepository.save(verificationToken);

            mailService.sendOtpEmail(user, email, otp, "Xác thực đăng nhập 2 bước (2FA)", "TWO_FACTOR_OTP");
            activityLogService.log(user, email, "LOGIN_2FA_CHALLENGE", "SUCCESS", httpRequest, "Đã gửi mã OTP 2FA tới email");

            userRepository.save(user);

            return ApiResponse.ok("Vui lòng nhập mã OTP 6 số đã được gửi tới email của bạn",
                    AuthResponse.builder()
                            .requires2fa(true)
                            .message("Yêu cầu nhập mã OTP xác thực 2FA")
                            .build()
            );
        }

        // Tạo Tokens
        return ApiResponse.ok("Đăng nhập thành công", generateTokenResponse(user, httpRequest));
    }

    @Transactional
    public ApiResponse<AuthResponse> verify2fa(VerifyOtpRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("Người dùng không tồn tại"));

        String cachedOtp = redisService.getOtp(email);
        boolean isValid = false;

        if (cachedOtp != null && cachedOtp.equals(request.getOtpCode().trim())) {
            isValid = true;
            redisService.deleteOtp(email);
        } else {
            // Fallback DB check
            var tokenOpt = verificationTokenRepository.findFirstByUserAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(user, "TWO_FACTOR_OTP");
            if (tokenOpt.isPresent()) {
                VerificationToken token = tokenOpt.get();
                if (token.getExpiresAt().isAfter(LocalDateTime.now()) && token.getTokenCode().equals(request.getOtpCode().trim())) {
                    isValid = true;
                    token.setIsUsed(true);
                    token.setUsedAt(LocalDateTime.now());
                    verificationTokenRepository.save(token);
                }
            }
        }

        if (!isValid) {
            activityLogService.log(user, email, "LOGIN_2FA", "FAILED", httpRequest, "Mã OTP không chính xác hoặc đã hết hạn");
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn!");
        }

        activityLogService.log(user, email, "LOGIN_2FA", "SUCCESS", httpRequest, "Xác thực 2FA thành công");
        return ApiResponse.ok("Xác thực 2FA thành công", generateTokenResponse(user, httpRequest));
    }

    @Transactional
    public ApiResponse<AuthResponse> refreshToken(RefreshTokenRequest request, HttpServletRequest httpRequest) {
        String refreshTokenStr = request.getRefreshToken();

        // 1. Kiểm tra làm mới token từ Keycloak nếu bật Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            Map<String, Object> refreshed = keycloakAdminService.refreshKeycloakToken(refreshTokenStr);
            if (refreshed != null && refreshed.containsKey("access_token")) {
                String newAccess = (String) refreshed.get("access_token");
                String newRefresh = (String) refreshed.getOrDefault("refresh_token", refreshTokenStr);
                long expiresIn = ((Number) refreshed.getOrDefault("expires_in", 300)).longValue();

                return ApiResponse.ok("Làm mới token từ Keycloak thành công",
                        AuthResponse.builder()
                                .accessToken(newAccess)
                                .refreshToken(newRefresh)
                                .tokenType("Bearer")
                                .expiresIn(expiresIn)
                                .idpProvider("keycloak")
                                .build()
                );
            }
        }

        // 2. Làm mới Token nội bộ (Self-IDP)
        if (!jwtUtils.validateJwtToken(refreshTokenStr)) {
            throw new UnauthorizedException("Refresh Token không hợp lệ hoặc đã hết hạn");
        }

        String email = jwtUtils.getUsernameFromJwtToken(refreshTokenStr);

        RefreshToken dbToken = refreshTokenRepository.findByToken(refreshTokenStr)
                .orElseThrow(() -> new UnauthorizedException("Refresh Token không tồn tại trong hệ thống"));

        if (Boolean.TRUE.equals(dbToken.getIsRevoked())) {
            throw new UnauthorizedException("Refresh Token này đã bị thu hồi trước đó");
        }

        if (dbToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new UnauthorizedException("Refresh Token đã hết hạn");
        }

        User user = dbToken.getUser();
        String newAccessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getId());

        activityLogService.log(user, email, "REFRESH_TOKEN", "SUCCESS", httpRequest, "Làm mới Access Token thành công");

        return ApiResponse.ok("Làm mới Token thành công",
                AuthResponse.builder()
                        .accessToken(newAccessToken)
                        .refreshToken(refreshTokenStr)
                        .tokenType("Bearer")
                        .expiresIn(jwtUtils.getExpirationMs())
                        .requires2fa(false)
                        .user(mapToProfileResponse(user))
                        .build()
        );
    }

    @Transactional
    public ApiResponse<String> logout(String authHeader, String refreshTokenStr, HttpServletRequest httpRequest) {
        // Đồng bộ logout trên Keycloak nếu bật Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            keycloakAdminService.logoutKeycloak(refreshTokenStr);
        }

        String userEmail = null;
        User user = null;

        if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
            String accessToken = authHeader.substring(7);
            if (jwtUtils.validateJwtToken(accessToken)) {
                String jwtId = jwtUtils.getJwtIdFromToken(accessToken);
                Date expiration = jwtUtils.getExpirationDateFromToken(accessToken);
                long ttl = expiration.getTime() - System.currentTimeMillis();

                userEmail = jwtUtils.getUsernameFromJwtToken(accessToken);
                user = userRepository.findByEmail(userEmail).orElse(null);

                if (ttl > 0) {
                    redisService.addToBlacklist(jwtId, ttl);

                    TokenBlacklist blacklist = TokenBlacklist.builder()
                            .jwtId(jwtId)
                            .user(user)
                            .tokenType("ACCESS")
                            .reason("LOGOUT")
                            .revokedAt(LocalDateTime.now())
                            .expiresAt(LocalDateTime.now().plusSeconds(ttl / 1000))
                            .build();
                    tokenBlacklistRepository.save(blacklist);
                }
            }
        }

        if (StringUtils.hasText(refreshTokenStr)) {
            redisService.deleteRefreshToken(refreshTokenStr);
            refreshTokenRepository.findByToken(refreshTokenStr).ifPresent(rt -> {
                rt.setIsRevoked(true);
                rt.setRevokedAt(LocalDateTime.now());
                rt.setRevokedReason("LOGOUT");
                refreshTokenRepository.save(rt);
            });
        }

        activityLogService.log(user, userEmail, "LOGOUT", "SUCCESS", httpRequest, "Đăng xuất tài khoản và thu hồi Token");
        return ApiResponse.ok("Đăng xuất thành công, Token đã được đưa vào Blacklist");
    }

    @Transactional
    public ApiResponse<String> forgotPassword(ForgotPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tài khoản với email này!"));

        String otp = generateNumericOtp(6);
        redisService.storeOtp(email, otp, 15);

        VerificationToken verificationToken = VerificationToken.builder()
                .user(user)
                .tokenCode(otp)
                .tokenType("PASSWORD_RESET")
                .deliveryChannel("EMAIL")
                .destination(email)
                .attempts(0)
                .maxAttempts(5)
                .expiresAt(LocalDateTime.now().plusMinutes(15))
                .isUsed(false)
                .createdAt(LocalDateTime.now())
                .build();
        verificationTokenRepository.save(verificationToken);

        mailService.sendOtpEmail(user, email, otp, "Đặt lại mật khẩu (Forgot Password)", "PASSWORD_RESET");
        activityLogService.log(user, email, "FORGOT_PASSWORD_REQUEST", "SUCCESS", httpRequest, "Đã gửi mã OTP đặt lại mật khẩu");

        return ApiResponse.ok("Mã xác thực OTP đặt lại mật khẩu đã được gửi đến email của bạn!");
    }

    @Transactional
    public ApiResponse<String> resetPassword(ResetPasswordRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Tài khoản không tồn tại!"));

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu mới và xác nhận mật khẩu không khớp!");
        }

        String cachedOtp = redisService.getOtp(email);
        boolean isValid = false;

        if (cachedOtp != null && cachedOtp.equals(request.getOtpCode().trim())) {
            isValid = true;
            redisService.deleteOtp(email);
        } else {
            var tokenOpt = verificationTokenRepository.findFirstByUserAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(user, "PASSWORD_RESET");
            if (tokenOpt.isPresent()) {
                VerificationToken token = tokenOpt.get();
                if (token.getExpiresAt().isAfter(LocalDateTime.now()) && token.getTokenCode().equals(request.getOtpCode().trim())) {
                    isValid = true;
                    token.setIsUsed(true);
                    token.setUsedAt(LocalDateTime.now());
                    verificationTokenRepository.save(token);
                }
            }
        }

        if (!isValid) {
            activityLogService.log(user, email, "RESET_PASSWORD", "FAILED", httpRequest, "OTP đặt lại mật khẩu sai hoặc đã hết hạn");
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn!");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setIsLocked(false);
        userRepository.save(user);

        // Thu hồi toàn bộ refresh token cũ để bảo mật
        refreshTokenRepository.revokeAllUserTokens(user, LocalDateTime.now(), "RESET_PASSWORD");

        mailService.sendPasswordChangedAlert(user);
        activityLogService.log(user, email, "RESET_PASSWORD", "SUCCESS", httpRequest, "Đặt lại mật khẩu thành công");

        return ApiResponse.ok("Đặt lại mật khẩu thành công! Bạn có thể dùng mật khẩu mới để đăng nhập.");
    }

    private AuthResponse generateTokenResponse(User user, HttpServletRequest httpRequest) {
        String accessToken = jwtUtils.generateAccessToken(user.getEmail(), user.getId());
        String refreshJwtId = UUID.randomUUID().toString();
        String refreshTokenStr = jwtUtils.generateRefreshToken(user.getEmail(), user.getId(), refreshJwtId);

        // Lưu Refresh Token vào Database & Redis
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .token(refreshTokenStr)
                .jwtId(refreshJwtId)
                .deviceInfo(httpRequest != null ? httpRequest.getHeader("User-Agent") : null)
                .ipAddress(httpRequest != null ? httpRequest.getRemoteAddr() : null)
                .issuedAt(LocalDateTime.now())
                .expiresAt(LocalDateTime.now().plusSeconds(jwtUtils.getRefreshExpirationMs() / 1000))
                .isRevoked(false)
                .build();
        refreshTokenRepository.save(rt);

        redisService.storeRefreshToken(refreshTokenStr, user.getEmail(), jwtUtils.getRefreshExpirationMs());

        // Cập nhật thông tin lần đăng nhập cuối
        user.setLastLoginAt(LocalDateTime.now());
        if (httpRequest != null) {
            user.setLastLoginIp(httpRequest.getRemoteAddr());
        }
        userRepository.save(user);

        activityLogService.log(user, user.getEmail(), "LOGIN", "SUCCESS", httpRequest, "Đăng nhập thành công và cấp Token");

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshTokenStr)
                .tokenType("Bearer")
                .expiresIn(jwtUtils.getExpirationMs())
                .requires2fa(false)
                .user(mapToProfileResponse(user))
                .build();
    }

    public UserProfileResponse mapToProfileResponse(User user) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toSet());

        Set<String> permissions = new HashSet<>();
        user.getRoles().forEach(role -> {
            if (role.getPermissions() != null) {
                role.getPermissions().forEach(p -> permissions.add(p.getName()));
            }
        });

        return UserProfileResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .phoneNumber(user.getPhoneNumber())
                .address(user.getAddress())
                .dateOfBirth(user.getDateOfBirth())
                .gender(user.getGender())
                .avatarUrl(user.getAvatarUrl())
                .isEmailVerified(user.getIsEmailVerified())
                .isPhoneVerified(user.getIsPhoneVerified())
                .twoFactorEnabled(user.getTwoFactorEnabled())
                .twoFactorMethod(user.getTwoFactorMethod())
                .isActive(user.getIsActive())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .createdAt(user.getCreatedAt())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
