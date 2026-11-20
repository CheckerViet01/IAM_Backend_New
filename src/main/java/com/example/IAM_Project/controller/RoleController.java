package com.example.IAM_Project.controller;

import com.example.IAM_Project.dto.*;
import com.example.IAM_Project.service.RbacService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/roles")
@RequiredArgsConstructor
public class RoleController {

    private final RbacService rbacService;

    @GetMapping
    @PreAuthorize("hasPermission('ROLE', 'READ')")
    public ResponseEntity<ApiResponse<PaginatedResponse<RoleResponse>>> getRoles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort sorting = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.by(sortParts[0]).ascending()
                : Sort.by(sortParts[0]).descending();

        Pageable pageable = PageRequest.of(page, size, sorting);
        PaginatedResponse<RoleResponse> response = rbacService.getRoles(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách vai trò thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'READ')")
    public ResponseEntity<ApiResponse<RoleResponse>> getRoleById(@PathVariable Long id) {
        RoleResponse role = rbacService.getRoleById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết vai trò thành công", role));
    }

    @PostMapping
    @PreAuthorize("hasPermission('ROLE', 'CREATE')")
    public ResponseEntity<ApiResponse<RoleResponse>> createRole(@Valid @RequestBody RoleRequest request) {
        RoleResponse role = rbacService.createRole(request);
        return new ResponseEntity<>(ApiResponse.ok("Tạo vai trò thành công", role), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'UPDATE')")
    public ResponseEntity<ApiResponse<RoleResponse>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody RoleRequest request) {
        RoleResponse role = rbacService.updateRole(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật vai trò thành công", role));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('ROLE', 'DELETE')")
    public ResponseEntity<ApiResponse<String>> deleteRole(@PathVariable Long id) {
        rbacService.deleteRole(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa mềm vai trò ID " + id + " thành công"));
    }

    @PutMapping("/{id}/permissions")
    @PreAuthorize("hasPermission('ROLE', 'UPDATE')")
    public ResponseEntity<ApiResponse<RoleResponse>> assignPermissions(
            @PathVariable Long id,
            @RequestBody Set<Long> permissionIds) {
        RoleResponse role = rbacService.assignPermissionsToRole(id, permissionIds);
        return ResponseEntity.ok(ApiResponse.ok("Gán quyền cho vai trò thành công", role));
    }
}
