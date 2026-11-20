package com.example.IAM_Project.dto;

import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDetailResponse {

    private Long id;
    private String email;
    private String fullName;
    private String phoneNumber;
    private String address;
    private LocalDate dateOfBirth;
    private String gender;
    private String avatarUrl;
    private Boolean isEmailVerified;
    private Boolean isPhoneVerified;
    private Boolean twoFactorEnabled;
    private Boolean isActive;
    private Boolean isLocked;
    private Boolean deleted;
    private String createdBy;
    private String updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime lastLoginAt;
    private String lastLoginIp;
    private Set<String> roles;
    private Set<String> permissions;
}
