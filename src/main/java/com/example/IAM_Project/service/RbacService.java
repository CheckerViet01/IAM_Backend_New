package com.example.IAM_Project.service;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.entity.Permission;
import com.example.IAM_Project.entity.Role;
import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.exception.BadRequestException;
import com.example.IAM_Project.exception.ResourceNotFoundException;
import com.example.IAM_Project.repository.PermissionRepository;
import com.example.IAM_Project.repository.RoleRepository;
import com.example.IAM_Project.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RbacService {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRepository userRepository;

    // ==========================================
    // 1. QUẢN LÝ QUYỀN (PERMISSIONS)
    // ==========================================

    @Transactional(readOnly = true)
    public PaginatedResponse<PermissionResponse> getPermissions(Pageable pageable) {
        Page<Permission> page = permissionRepository.findAll(pageable);
        return PaginatedResponse.from(page.map(this::mapToPermissionResponse));
    }

    @Transactional(readOnly = true)
    public PermissionResponse getPermissionById(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền với ID: " + id));
        return mapToPermissionResponse(permission);
    }

    @Transactional
    public PermissionResponse createPermission(PermissionRequest request) {
        String permName = request.getName().trim().toUpperCase();
        if (permissionRepository.findByName(permName).isPresent()) {
            throw new BadRequestException("Quyền " + permName + " đã tồn tại trong hệ thống!");
        }

        Permission permission = Permission.builder()
                .name(permName)
                .module(request.getModule().trim().toUpperCase())
                .description(request.getDescription())
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .build();

        Permission saved = permissionRepository.save(permission);
        log.info("Đã tạo mới quyền: {}", saved.getName());
        return mapToPermissionResponse(saved);
    }

    @Transactional
    public PermissionResponse updatePermission(Long id, PermissionRequest request) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền với ID: " + id));

        if (request.getName() != null && !request.getName().isBlank()) {
            permission.setName(request.getName().trim().toUpperCase());
        }
        if (request.getModule() != null && !request.getModule().isBlank()) {
            permission.setModule(request.getModule().trim().toUpperCase());
        }
        if (request.getDescription() != null) {
            permission.setDescription(request.getDescription());
        }
        permission.setUpdatedAt(LocalDateTime.now());

        Permission updated = permissionRepository.save(permission);
        log.info("Đã cập nhật quyền ID: {}", updated.getId());
        return mapToPermissionResponse(updated);
    }

    @Transactional
    public void deletePermission(Long id) {
        Permission permission = permissionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy quyền với ID: " + id));

        // Soft delete: chuyển deleted = true
        permission.setDeleted(true);
        permission.setUpdatedAt(LocalDateTime.now());
        permissionRepository.save(permission);
        log.info("Đã xóa mềm quyền ID: {}", id);
    }

    // ==========================================
    // 2. QUẢN LÝ VAI TRÒ (ROLES)
    // ==========================================

    @Transactional(readOnly = true)
    public PaginatedResponse<RoleResponse> getRoles(Pageable pageable) {
        Page<Role> page = roleRepository.findAll(pageable);
        return PaginatedResponse.from(page.map(this::mapToRoleResponse));
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));
        return mapToRoleResponse(role);
    }

    @Transactional
    public RoleResponse createRole(RoleRequest request) {
        String roleName = request.getName().trim().toUpperCase();
        if (roleRepository.findByName(roleName).isPresent()) {
            throw new BadRequestException("Vai trò " + roleName + " đã tồn tại trong hệ thống!");
        }

        Role role = Role.builder()
                .name(roleName)
                .description(request.getDescription())
                .deleted(false)
                .createdAt(LocalDateTime.now())
                .build();

        if (request.getPermissionIds() != null && !request.getPermissionIds().isEmpty()) {
            List<Permission> perms = permissionRepository.findAllById(request.getPermissionIds());
            role.setPermissions(new HashSet<>(perms));
        }

        Role saved = roleRepository.save(role);
        log.info("Đã tạo mới vai trò: {}", saved.getName());
        return mapToRoleResponse(saved);
    }

    @Transactional
    public RoleResponse updateRole(Long id, RoleRequest request) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));

        if (request.getDescription() != null) {
            role.setDescription(request.getDescription());
        }
        if (request.getPermissionIds() != null) {
            List<Permission> perms = permissionRepository.findAllById(request.getPermissionIds());
            role.setPermissions(new HashSet<>(perms));
        }
        role.setUpdatedAt(LocalDateTime.now());

        Role updated = roleRepository.save(role);
        log.info("Đã cập nhật vai trò ID: {}", updated.getId());
        return mapToRoleResponse(updated);
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + id));

        // Soft delete: chuyển deleted = true
        role.setDeleted(true);
        role.setUpdatedAt(LocalDateTime.now());
        roleRepository.save(role);
        log.info("Đã xóa mềm vai trò ID: {}", id);
    }

    @Transactional
    public RoleResponse assignPermissionsToRole(Long roleId, Set<Long> permissionIds) {
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vai trò với ID: " + roleId));

        if (permissionIds == null || permissionIds.isEmpty()) {
            role.getPermissions().clear();
        } else {
            List<Permission> perms = permissionRepository.findAllById(permissionIds);
            role.setPermissions(new HashSet<>(perms));
        }
        role.setUpdatedAt(LocalDateTime.now());

        Role saved = roleRepository.save(role);
        log.info("Đã gán {} quyền cho vai trò {}", saved.getPermissions().size(), saved.getName());
        return mapToRoleResponse(saved);
    }

    // ==========================================
    // MAPPER HELPERS
    // ==========================================

    public PermissionResponse mapToPermissionResponse(Permission p) {
        return PermissionResponse.builder()
                .id(p.getId())
                .name(p.getName())
                .module(p.getModule())
                .description(p.getDescription())
                .deleted(p.getDeleted())
                .createdBy(p.getCreatedBy())
                .updatedBy(p.getUpdatedBy())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }

    public RoleResponse mapToRoleResponse(Role r) {
        Set<PermissionResponse> perms = r.getPermissions() != null
                ? r.getPermissions().stream().map(this::mapToPermissionResponse).collect(Collectors.toSet())
                : Collections.emptySet();

        return RoleResponse.builder()
                .id(r.getId())
                .name(r.getName())
                .description(r.getDescription())
                .deleted(r.getDeleted())
                .createdBy(r.getCreatedBy())
                .updatedBy(r.getUpdatedBy())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .permissions(perms)
                .build();
    }
}
