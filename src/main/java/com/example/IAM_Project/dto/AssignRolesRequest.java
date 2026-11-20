package com.example.IAM_Project.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssignRolesRequest {
    @NotEmpty(message = "Danh sách vai trò không được để trống")
    private Set<String> roles;
}
