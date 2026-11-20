package com.example.IAM_Project.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStatusRequest {
    private Boolean locked;
    private Boolean active;
}
