package com.example.IAM_Project.dto;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class KeycloakConfigResponse {
    private boolean enabled;
    private String serverUrl;
    private String realm;
    private String clientId;
    private String loginUrl;
}
