package com.example.IAM_Project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RoleRequest {

    @NotBlank(message = "Tên vai trò không được để trống")
    private String name;

    private String description;
    private Set<Long> permissionIds;
}
