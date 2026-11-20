package com.example.IAM_Project.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";
        return new OpenAPI()
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(securitySchemeName,
                        new SecurityScheme()
                                .name(securitySchemeName)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Nhập Bearer JWT token (cả Self-IDP hoặc Keycloak access_token)")))
                .info(new Info()
                        .title("IAM Shield - Enterprise Identity & Access Management API")
                        .version("2.0.0")
                        .description("Hệ thống quản lý định danh và phân quyền IAM Service 2 hỗ trợ cơ chế Dual-IDP (Self-IDP & Keycloak OIDC), RBAC với hasPermission, JPA Auditing, Soft Delete và Logging rolling.")
                        .contact(new Contact().name("IAM Development Team").email("dev@iam.local"))
                        .license(new License().name("Apache 2.0").url("https://spring.io")));
    }
}
