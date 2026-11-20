package com.example.IAM_Project.dto;

import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuthResponse {
    private String accessToken;
    private String refreshToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private Long expiresIn;
    private boolean requires2fa;
    private String message;
    private String loginUrl;
    private String idpProvider;
    private UserProfileResponse user;
}
