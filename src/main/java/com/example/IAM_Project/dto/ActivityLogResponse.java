package com.example.IAM_Project.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivityLogResponse {
    private Long id;
    private String action;
    private String status;
    private String ipAddress;
    private String userAgent;
    private String details;
    private LocalDateTime createdAt;
}
