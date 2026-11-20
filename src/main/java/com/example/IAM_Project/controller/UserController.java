package com.example.IAM_Project.controller;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @GetMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile(Authentication authentication) {
        UserProfileResponse profile = userService.getProfile(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Lấy thông tin cá nhân thành công", profile));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest request,
            HttpServletRequest httpRequest) {
        UserProfileResponse profile = userService.updateProfile(authentication.getName(), request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật thông tin cá nhân thành công", profile));
    }

    @PostMapping("/change-password")
    public ResponseEntity<ApiResponse<String>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = userService.changePassword(authentication.getName(), request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<String>> uploadAvatar(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = userService.uploadAvatar(authentication.getName(), file, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/avatars")
    public ResponseEntity<ApiResponse<List<AvatarResponse>>> getUserAvatars(Authentication authentication) {
        List<AvatarResponse> avatars = userService.getUserAvatars(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách ảnh đại diện thành công", avatars));
    }

    @PostMapping("/2fa/request-enable")
    public ResponseEntity<ApiResponse<String>> requestEnable2fa(
            Authentication authentication,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = userService.requestEnable2fa(authentication.getName(), httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/confirm-enable")
    public ResponseEntity<ApiResponse<String>> confirmEnable2fa(
            Authentication authentication,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String otpCode = body.get("otpCode");
        ApiResponse<String> response = userService.confirmEnable2fa(authentication.getName(), otpCode, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/2fa/disable")
    public ResponseEntity<ApiResponse<String>> disable2fa(
            Authentication authentication,
            @RequestBody Map<String, String> body,
            HttpServletRequest httpRequest) {
        String password = body.get("password");
        ApiResponse<String> response = userService.disable2fa(authentication.getName(), password, httpRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/activity-logs")
    public ResponseEntity<ApiResponse<Page<ActivityLogResponse>>> getActivityLogs(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<ActivityLogResponse> logs = userService.getUserActivityLogs(authentication.getName(), pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy lịch sử hoạt động thành công", logs));
    }

    // ==========================================
    // CÁC API QUẢN LÝ NGƯỜI DÙNG (YÊU CẦU 2 & RBAC)
    // ==========================================

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'CREATE')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> createUser(
            @Valid @RequestBody UserCreateRequest request,
            HttpServletRequest httpRequest) {
        UserDetailResponse user = userService.createUser(request, httpRequest);
        return new ResponseEntity<>(ApiResponse.ok("Tạo người dùng thành công", user), HttpStatus.CREATED);
    }

    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'READ')")
    public ResponseEntity<ApiResponse<PaginatedResponse<UserDetailResponse>>> getUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String[] sortParts = sort.split(",");
        org.springframework.data.domain.Sort sorting = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? org.springframework.data.domain.Sort.by(sortParts[0]).ascending()
                : org.springframework.data.domain.Sort.by(sortParts[0]).descending();

        Pageable pageable = PageRequest.of(page, size, sorting);
        PaginatedResponse<UserDetailResponse> response = userService.getUsers(keyword, pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách người dùng thành công", response));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'READ')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> getUserDetail(@PathVariable Long id) {
        UserDetailResponse user = userService.getUserDetail(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết người dùng thành công", user));
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'DELETE')")
    public ResponseEntity<ApiResponse<String>> deleteUser(@PathVariable Long id, HttpServletRequest httpRequest) {
        userService.softDeleteUser(id, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Xóa mềm người dùng ID " + id + " thành công"));
    }

    @PutMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'UPDATE')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> updateUserStatus(
            @PathVariable Long id,
            @RequestBody UserStatusRequest request,
            HttpServletRequest httpRequest) {
        UserDetailResponse user = userService.updateUserStatus(id, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật trạng thái người dùng thành công", user));
    }

    @PostMapping("/{id}/reset-password")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'UPDATE')")
    public ResponseEntity<ApiResponse<String>> resetUserPassword(
            @PathVariable Long id,
            @Valid @RequestBody ResetPasswordByAdminRequest request,
            HttpServletRequest httpRequest) {
        ApiResponse<String> response = userService.resetPasswordByAdmin(id, request, httpRequest);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}/roles")
    @org.springframework.security.access.prepost.PreAuthorize("hasPermission('USER', 'UPDATE')")
    public ResponseEntity<ApiResponse<UserDetailResponse>> assignRoles(
            @PathVariable Long id,
            @Valid @RequestBody AssignRolesRequest request,
            HttpServletRequest httpRequest) {
        UserDetailResponse user = userService.assignRolesToUser(id, request, httpRequest);
        return ResponseEntity.ok(ApiResponse.ok("Gán vai trò cho người dùng thành công", user));
    }
}
