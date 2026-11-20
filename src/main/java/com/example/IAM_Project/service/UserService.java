package com.example.IAM_Project.service;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.UserActivityLog;
import com.example.IAM_Project.entity.UserAvatar;
import com.example.IAM_Project.entity.VerificationToken;
import com.example.IAM_Project.exception.BadRequestException;
import com.example.IAM_Project.exception.ResourceNotFoundException;
import com.example.IAM_Project.repository.UserActivityLogRepository;
import com.example.IAM_Project.repository.UserAvatarRepository;
import com.example.IAM_Project.repository.UserRepository;
import com.example.IAM_Project.repository.VerificationTokenRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserAvatarRepository userAvatarRepository;
    private final UserActivityLogRepository activityLogRepository;
    private final VerificationTokenRepository verificationTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthService authService;
    private final FileStorageService fileStorageService;
    private final MailService mailService;
    private final RedisService redisService;
    private final ActivityLogService activityLogService;
    private final com.example.IAM_Project.repository.RoleRepository roleRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));
        return authService.mapToProfileResponse(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(String email, UpdateProfileRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName().trim());
        }
        if (request.getPhoneNumber() != null) {
            user.setPhoneNumber(request.getPhoneNumber().trim());
        }
        if (request.getAddress() != null) {
            user.setAddress(request.getAddress().trim());
        }
        if (request.getDateOfBirth() != null) {
            user.setDateOfBirth(request.getDateOfBirth());
        }
        if (request.getGender() != null) {
            user.setGender(request.getGender().trim());
        }

        user.setUpdatedAt(LocalDateTime.now());
        User savedUser = userRepository.save(user);

        // Gửi email thông báo cập nhật hồ sơ
        try {
            mailService.sendProfileUpdatedAlert(savedUser);
        } catch (Exception ignored) {}

        activityLogService.log(savedUser, email, "UPDATE_PROFILE", "SUCCESS", httpRequest, "Cập nhật thông tin cá nhân");

        return authService.mapToProfileResponse(savedUser);
    }

    @Transactional
    public ApiResponse<String> changePassword(String email, ChangePasswordRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            activityLogService.log(user, email, "CHANGE_PASSWORD", "FAILED", httpRequest, "Mật khẩu cũ không chính xác");
            throw new BadRequestException("Mật khẩu hiện tại không chính xác!");
        }

        if (request.getOldPassword().equals(request.getNewPassword())) {
            throw new BadRequestException("Mật khẩu mới không được trùng với mật khẩu cũ!");
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BadRequestException("Mật khẩu mới và xác nhận mật khẩu không khớp!");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        userRepository.save(user);

        // Gửi email cảnh báo đổi mật khẩu
        try {
            mailService.sendPasswordChangedAlert(user);
        } catch (Exception ignored) {}

        activityLogService.log(user, email, "CHANGE_PASSWORD", "SUCCESS", httpRequest, "Đổi mật khẩu thành công");

        return ApiResponse.ok("Đổi mật khẩu thành công! Hãy sử dụng mật khẩu mới cho các lần đăng nhập tiếp theo.");
    }

    @Transactional
    public ApiResponse<String> uploadAvatar(String email, MultipartFile file, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        String avatarUrl = fileStorageService.storeAvatar(file, user);
        user.setAvatarUrl(avatarUrl);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        activityLogService.log(user, email, "UPLOAD_AVATAR", "SUCCESS", httpRequest, "Cập nhật ảnh đại diện mới");

        return ApiResponse.ok("Cập nhật ảnh đại diện thành công!", avatarUrl);
    }

    @Transactional(readOnly = true)
    public List<AvatarResponse> getUserAvatars(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        List<UserAvatar> avatars = userAvatarRepository.findByUserOrderByUploadedAtDesc(user);
        return avatars.stream().map(a -> AvatarResponse.builder()
                .id(a.getId())
                .fileName(a.getFileName())
                .originalFileName(a.getOriginalFileName())
                .fileUrl(a.getFilePathOrUrl())
                .fileSizeBytes(a.getFileSizeBytes())
                .isCurrent(a.getIsCurrent())
                .uploadedAt(a.getUploadedAt())
                .build()
        ).collect(Collectors.toList());
    }

    @Transactional
    public ApiResponse<String> requestEnable2fa(String email, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (Boolean.TRUE.equals(user.getTwoFactorEnabled())) {
            throw new BadRequestException("Tính năng xác thực hai yếu tố (2FA) đã được kích hoạt trên tài khoản này!");
        }

        String otp = authService.generateNumericOtp(6);
        redisService.storeOtp(email, otp, 10);

        VerificationToken token = VerificationToken.builder()
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
        verificationTokenRepository.save(token);

        mailService.sendOtpEmail(user, email, otp, "Kích hoạt xác thực 2 bước (2FA)", "TWO_FACTOR_OTP");
        activityLogService.log(user, email, "REQUEST_ENABLE_2FA", "SUCCESS", httpRequest, "Gửi mã OTP yêu cầu bật 2FA");

        return ApiResponse.ok("Mã xác nhận OTP đã được gửi đến email của bạn để xác nhận bật 2FA.");
    }

    @Transactional
    public ApiResponse<String> confirmEnable2fa(String email, String otpCode, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        String cachedOtp = redisService.getOtp(email);
        boolean isValid = false;

        if (cachedOtp != null && cachedOtp.equals(otpCode.trim())) {
            isValid = true;
            redisService.deleteOtp(email);
        } else {
            var tokenOpt = verificationTokenRepository.findFirstByUserAndTokenTypeAndIsUsedFalseOrderByCreatedAtDesc(user, "TWO_FACTOR_OTP");
            if (tokenOpt.isPresent()) {
                VerificationToken token = tokenOpt.get();
                if (token.getExpiresAt().isAfter(LocalDateTime.now()) && token.getTokenCode().equals(otpCode.trim())) {
                    isValid = true;
                    token.setIsUsed(true);
                    token.setUsedAt(LocalDateTime.now());
                    verificationTokenRepository.save(token);
                }
            }
        }

        if (!isValid) {
            activityLogService.log(user, email, "CONFIRM_ENABLE_2FA", "FAILED", httpRequest, "Mã OTP bật 2FA sai hoặc đã hết hạn");
            throw new BadRequestException("Mã OTP không chính xác hoặc đã hết hạn!");
        }

        user.setTwoFactorEnabled(true);
        user.setTwoFactorMethod("EMAIL");
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        activityLogService.log(user, email, "ENABLE_2FA", "SUCCESS", httpRequest, "Kích hoạt thành công 2FA bằng Email OTP");

        return ApiResponse.ok("Đã kích hoạt xác thực 2 yếu tố (2FA) thành công! Mỗi lần đăng nhập sẽ yêu cầu mã OTP gửi qua email.");
    }

    @Transactional
    public ApiResponse<String> disable2fa(String email, String password, HttpServletRequest httpRequest) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            activityLogService.log(user, email, "DISABLE_2FA", "FAILED", httpRequest, "Mật khẩu xác nhận tắt 2FA không chính xác");
            throw new BadRequestException("Mật khẩu không chính xác!");
        }

        user.setTwoFactorEnabled(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        activityLogService.log(user, email, "DISABLE_2FA", "SUCCESS", httpRequest, "Hủy kích hoạt 2FA thành công");

        return ApiResponse.ok("Đã tắt xác thực hai yếu tố (2FA) thành công.");
    }

    @Transactional(readOnly = true)
    public Page<ActivityLogResponse> getUserActivityLogs(String email, Pageable pageable) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Người dùng không tồn tại"));

        Page<UserActivityLog> page = activityLogRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return page.map(log -> ActivityLogResponse.builder()
                .id(log.getId())
                .action(log.getAction())
                .status(log.getStatus())
                .ipAddress(log.getIpAddress())
                .userAgent(log.getUserAgent())
                .details(log.getDetails())
                .createdAt(log.getCreatedAt())
                .build()
        );
    }

    // ==========================================
    // CÁC API QUẢN LÝ NGƯỜI DÙNG (YÊU CẦU 2 & RBAC)
    // ==========================================

    @Transactional
    public UserDetailResponse createUser(UserCreateRequest request, HttpServletRequest httpRequest) {
        String email = request.getEmail().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new BadRequestException("Email đã tồn tại trong hệ thống!");
        }

        Set<com.example.IAM_Project.entity.Role> roles = new java.util.HashSet<>();
        if (request.getRoles() != null && !request.getRoles().isEmpty()) {
            for (String roleName : request.getRoles()) {
                roleRepository.findByName(roleName.trim().toUpperCase()).ifPresent(roles::add);
            }
        }
        if (roles.isEmpty()) {
            roleRepository.findByName("ROLE_USER").ifPresent(roles::add);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .fullName(request.getFullName() != null ? request.getFullName().trim() : null)
                .phoneNumber(request.getPhoneNumber() != null ? request.getPhoneNumber().trim() : null)
                .address(request.getAddress() != null ? request.getAddress().trim() : null)
                .gender(request.getGender() != null ? request.getGender().trim() : null)
                .roles(roles)
                .isActive(true)
                .isLocked(false)
                .deleted(false)
                .failedLoginAttempts(0)
                .isEmailVerified(true)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        User savedUser = userRepository.save(user);

        // Đồng bộ tạo user trên Keycloak nếu bật Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            keycloakAdminService.createKeycloakUser(email, request.getPassword(), request.getFullName(), true);
        }

        activityLogService.log(savedUser, email, "ADMIN_CREATE_USER", "SUCCESS", httpRequest, "Admin tạo mới người dùng");
        return mapToUserDetailResponse(savedUser);
    }

    @Transactional
    public void softDeleteUser(Long id, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        user.setDeleted(true);
        user.setIsActive(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Đồng bộ tắt user trên Keycloak nếu bật Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            keycloakAdminService.deleteKeycloakUser(user.getEmail());
        }

        activityLogService.log(user, user.getEmail(), "ADMIN_DELETE_USER", "SUCCESS", httpRequest, "Xóa mềm người dùng ID: " + id);
    }

    @Transactional
    public UserDetailResponse updateUserStatus(Long id, UserStatusRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        if (request.getLocked() != null) {
            user.setIsLocked(request.getLocked());
            if (!request.getLocked()) {
                user.setFailedLoginAttempts(0);
                user.setLockedUntil(null);
            }
        }
        if (request.getActive() != null) {
            user.setIsActive(request.getActive());
        }
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        // Đồng bộ trạng thái sang Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            boolean enabled = !Boolean.TRUE.equals(saved.getIsLocked()) && Boolean.TRUE.equals(saved.getIsActive());
            keycloakAdminService.updateUserStatus(saved.getEmail(), enabled);
        }

        activityLogService.log(saved, saved.getEmail(), "ADMIN_UPDATE_STATUS", "SUCCESS", httpRequest, 
                "Cập nhật trạng thái: locked=" + saved.getIsLocked() + ", active=" + saved.getIsActive());

        return mapToUserDetailResponse(saved);
    }

    @Transactional
    public ApiResponse<String> resetPasswordByAdmin(Long id, ResetPasswordByAdminRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setPasswordChangedAt(LocalDateTime.now());
        user.setFailedLoginAttempts(0);
        user.setIsLocked(false);
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Đồng bộ sang Keycloak
        if (keycloakAdminService.isKeycloakEnabled()) {
            keycloakAdminService.resetUserPassword(user.getEmail(), request.getNewPassword());
        }

        activityLogService.log(user, user.getEmail(), "ADMIN_RESET_PASSWORD", "SUCCESS", httpRequest, "Admin đặt lại mật khẩu");
        return ApiResponse.ok("Đặt lại mật khẩu cho người dùng " + user.getEmail() + " thành công!");
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<UserDetailResponse> getUsers(String keyword, Pageable pageable) {
        Page<User> page;
        if (keyword != null && !keyword.isBlank()) {
            String trimmed = keyword.trim();
            page = userRepository.findByEmailContainingIgnoreCaseOrFullNameContainingIgnoreCase(trimmed, trimmed, pageable);
        } else {
            page = userRepository.findAll(pageable);
        }
        return PaginatedResponse.from(page.map(this::mapToUserDetailResponse));
    }

    @Transactional(readOnly = true)
    public UserDetailResponse getUserDetail(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));
        return mapToUserDetailResponse(user);
    }

    @Transactional
    public UserDetailResponse assignRolesToUser(Long id, AssignRolesRequest request, HttpServletRequest httpRequest) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng với ID: " + id));

        Set<com.example.IAM_Project.entity.Role> newRoles = new java.util.HashSet<>();
        for (String roleName : request.getRoles()) {
            roleRepository.findByName(roleName.trim().toUpperCase()).ifPresent(newRoles::add);
        }
        user.setRoles(newRoles);
        user.setUpdatedAt(LocalDateTime.now());
        User saved = userRepository.save(user);

        activityLogService.log(saved, saved.getEmail(), "ADMIN_ASSIGN_ROLES", "SUCCESS", httpRequest, 
                "Gán vai trò mới: " + request.getRoles());

        return mapToUserDetailResponse(saved);
    }

    public UserDetailResponse mapToUserDetailResponse(User user) {
        Set<String> roles = user.getRoles() != null
                ? user.getRoles().stream().map(com.example.IAM_Project.entity.Role::getName).collect(Collectors.toSet())
                : java.util.Collections.emptySet();

        Set<String> permissions = new java.util.HashSet<>();
        if (user.getRoles() != null) {
            user.getRoles().forEach(role -> {
                if (role.getPermissions() != null) {
                    role.getPermissions().forEach(p -> permissions.add(p.getName()));
                }
            });
        }

        return UserDetailResponse.builder()
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
                .isActive(user.getIsActive())
                .isLocked(user.getIsLocked())
                .deleted(user.getDeleted())
                .createdBy(user.getCreatedBy())
                .updatedBy(user.getUpdatedBy())
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .lastLoginAt(user.getLastLoginAt())
                .lastLoginIp(user.getLastLoginIp())
                .roles(roles)
                .permissions(permissions)
                .build();
    }
}
