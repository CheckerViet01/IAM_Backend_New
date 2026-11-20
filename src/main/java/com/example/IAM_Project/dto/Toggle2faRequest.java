package com.example.IAM_Project.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Toggle2faRequest {
    private boolean enable;
    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    private String currentPassword;
    private String otpCode;
}
