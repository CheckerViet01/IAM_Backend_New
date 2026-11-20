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

@RestController
@RequestMapping("/api/v1/permissions")
@RequiredArgsConstructor
public class PermissionController {

    private final RbacService rbacService;

    @GetMapping
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    public ResponseEntity<ApiResponse<PaginatedResponse<PermissionResponse>>> getPermissions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id,desc") String sort) {

        String[] sortParts = sort.split(",");
        Sort sorting = sortParts.length > 1 && "asc".equalsIgnoreCase(sortParts[1])
                ? Sort.by(sortParts[0]).ascending()
                : Sort.by(sortParts[0]).descending();

        Pageable pageable = PageRequest.of(page, size, sorting);
        PaginatedResponse<PermissionResponse> response = rbacService.getPermissions(pageable);
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách quyền thành công", response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasPermission('PERMISSION', 'READ')")
    public ResponseEntity<ApiResponse<PermissionResponse>> getPermissionById(@PathVariable Long id) {
        PermissionResponse permission = rbacService.getPermissionById(id);
        return ResponseEntity.ok(ApiResponse.ok("Lấy chi tiết quyền thành công", permission));
    }

    @PostMapping
    @PreAuthorize("hasPermission('PERMISSION', 'CREATE')")
    public ResponseEntity<ApiResponse<PermissionResponse>> createPermission(@Valid @RequestBody PermissionRequest request) {
        PermissionResponse permission = rbacService.createPermission(request);
        return new ResponseEntity<>(ApiResponse.ok("Tạo quyền thành công", permission), HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasPermission('PERMISSION', 'UPDATE')")
    public ResponseEntity<ApiResponse<PermissionResponse>> updatePermission(
            @PathVariable Long id,
            @Valid @RequestBody PermissionRequest request) {
        PermissionResponse permission = rbacService.updatePermission(id, request);
        return ResponseEntity.ok(ApiResponse.ok("Cập nhật quyền thành công", permission));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasPermission('PERMISSION', 'DELETE')")
    public ResponseEntity<ApiResponse<String>> deletePermission(@PathVariable Long id) {
        rbacService.deletePermission(id);
        return ResponseEntity.ok(ApiResponse.ok("Xóa mềm quyền ID " + id + " thành công"));
    }
}
