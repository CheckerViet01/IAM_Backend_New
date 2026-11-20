package com.example.IAM_Project.config;

import com.example.IAM_Project.entity.Permission;
import com.example.IAM_Project.entity.Role;
import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.repository.PermissionRepository;
import com.example.IAM_Project.repository.RoleRepository;
import com.example.IAM_Project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final com.example.IAM_Project.service.KeycloakAdminService keycloakAdminService;

    @Override
    @Transactional
    public void run(String... args) {
        log.info("Khởi tạo dữ liệu vai trò mặc định và quyền hệ thống IAM Service 2...");

        // 1. Danh sách Permissions theo module
        Map<String, List<String>> modulePermissions = new LinkedHashMap<>();
        modulePermissions.put("USER", List.of("USER:CREATE", "USER:READ", "USER:UPDATE", "USER:DELETE"));
        modulePermissions.put("ROLE", List.of("ROLE:CREATE", "ROLE:READ", "ROLE:UPDATE", "ROLE:DELETE"));
        modulePermissions.put("PERMISSION", List.of("PERMISSION:CREATE", "PERMISSION:READ", "PERMISSION:UPDATE", "PERMISSION:DELETE"));
        modulePermissions.put("SYSTEM", List.of("SYSTEM:CONFIG"));

        Map<String, Permission> permissionMap = new HashMap<>();

        for (Map.Entry<String, List<String>> entry : modulePermissions.entrySet()) {
            String module = entry.getKey();
            for (String permName : entry.getValue()) {
                Permission perm = permissionRepository.findByName(permName)
                        .orElseGet(() -> {
                            Permission p = Permission.builder()
                                    .name(permName)
                                    .module(module)
                                    .description("Quyền " + permName)
                                    .deleted(false)
                                    .createdAt(LocalDateTime.now())
                                    .build();
                            return permissionRepository.save(p);
                        });
                permissionMap.put(permName, perm);
            }
        }

        // 2. Tạo các vai trò mặc định theo Yêu cầu 9 của đề bài:
        // Quản lý người dùng: Có quyền truy cập các API quản lý user
        Role userManagerRole = roleRepository.findByName("USER_MANAGER")
                .orElseGet(() -> Role.builder()
                        .name("USER_MANAGER")
                        .description("Quản lý người dùng: Có quyền truy cập các API quản lý user")
                        .deleted(false)
                        .createdAt(LocalDateTime.now())
                        .build());

        Set<Permission> userManagerPerms = new HashSet<>();
        userManagerPerms.add(permissionMap.get("USER:CREATE"));
        userManagerPerms.add(permissionMap.get("USER:READ"));
        userManagerPerms.add(permissionMap.get("USER:UPDATE"));
        userManagerPerms.add(permissionMap.get("USER:DELETE"));
        userManagerRole.setPermissions(userManagerPerms);
        roleRepository.save(userManagerRole);

        // Quản trị hệ thống: Có quyền truy cập các API quản lý quyền và vai trò
        Role systemAdminRole = roleRepository.findByName("SYSTEM_ADMIN")
                .orElseGet(() -> Role.builder()
                        .name("SYSTEM_ADMIN")
                        .description("Quản trị hệ thống: Có quyền truy cập các API quản lý quyền và vai trò")
                        .deleted(false)
                        .createdAt(LocalDateTime.now())
                        .build());

        Set<Permission> systemAdminPerms = new HashSet<>(permissionMap.values());
        systemAdminRole.setPermissions(systemAdminPerms);
        roleRepository.save(systemAdminRole);

        // Đảm bảo ROLE_USER cũng tồn tại
        Role userRole = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> roleRepository.save(Role.builder()
                        .name("ROLE_USER")
                        .description("Người dùng tiêu chuẩn")
                        .deleted(false)
                        .createdAt(LocalDateTime.now())
                        .build()));

        // Gán SYSTEM_ADMIN và USER_MANAGER cho tài khoản quản trị viên nếu có
        userRepository.findByEmail("hohoanluong2000k1@gmail.com").ifPresent(adminUser -> {
            Set<Role> roles = adminUser.getRoles() != null ? new HashSet<>(adminUser.getRoles()) : new HashSet<>();
            roles.add(systemAdminRole);
            roles.add(userManagerRole);
            adminUser.setRoles(roles);
            userRepository.save(adminUser);
            log.info("Đã gán vai trò SYSTEM_ADMIN và USER_MANAGER cho tài khoản: {}", adminUser.getEmail());

            if (keycloakAdminService.isKeycloakEnabled()) {
                keycloakAdminService.createKeycloakUser(adminUser.getEmail(), "Password@123", adminUser.getFullName(), true);
            }
        });

        log.info("Khởi tạo dữ liệu hệ thống IAM Service 2 thành công!");
    }
}
