package com.example.IAM_Project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PermissionRequest {

    @NotBlank(message = "Tên quyền không được để trống")
    private String name;

    @NotBlank(message = "Module không được để trống")
    private String module;

    private String description;
}
