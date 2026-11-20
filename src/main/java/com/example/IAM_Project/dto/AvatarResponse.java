package com.example.IAM_Project.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AvatarResponse {
    private Long id;
    private String fileName;
    private String originalFileName;
    private String fileUrl;
    private Long fileSizeBytes;
    private Boolean isCurrent;
    private LocalDateTime uploadedAt;
}
