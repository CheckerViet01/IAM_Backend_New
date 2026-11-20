package com.example.IAM_Project.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Slf4j
@Service
public class KeycloakAdminService {

    @Value("${app.idp.provider:keycloak}")
    private String idpProvider;

    @Value("${app.keycloak.server-url:http://localhost:8180}")
    private String serverUrl;

    @Value("${app.keycloak.realm:iam-realm}")
    private String realm;

    @Value("${app.keycloak.client-id:iam-frontend}")
    private String clientId;

    @Value("${app.keycloak.admin-client-id:iam-admin-client}")
    private String adminClientId;

    @Value("${app.keycloak.admin-client-secret:gRteLWut1BX6oVkBSmZexeV5wf6IwNMg8GaUFv94fpvragDBBTM04gmThFU29J5uDzGduVeHhs9keWAhrWABJX}")
    private String adminClientSecret;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private String cachedAdminToken = null;
    private long tokenExpiryTime = 0;

    public boolean isKeycloakEnabled() {
        return "keycloak".equalsIgnoreCase(idpProvider);
    }

    public String getLoginUrl() {
        return String.format("%s/realms/%s/protocol/openid-connect/auth?client_id=%s&response_type=code&scope=openid%%20profile%%20email&redirect_uri=http://localhost:4200/login",
                serverUrl, realm, clientId);
    }

    public synchronized String getAdminAccessToken() {
        long now = System.currentTimeMillis();
        if (cachedAdminToken != null && now < tokenExpiryTime - 30000) {
            return cachedAdminToken;
        }

        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "client_credentials");
        map.add("client_id", adminClientId);
        map.add("client_secret", adminClientSecret);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            cachedAdminToken = root.path("access_token").asText();
            int expiresIn = root.path("expires_in").asInt(300);
            tokenExpiryTime = now + (expiresIn * 1000L);
            return cachedAdminToken;
        } catch (Exception e) {
            log.error("Lỗi lấy Admin Access Token từ Keycloak: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tạo User trên Keycloak
     */
    public String createKeycloakUser(String email, String password, String fullName, boolean enabled) {
        if (!isKeycloakEnabled()) return null;

        String adminToken = getAdminAccessToken();
        if (adminToken == null) {
            log.warn("Không lấy được token admin Keycloak, bỏ qua đồng bộ tạo user Keycloak");
            return null;
        }

        String usersUrl = String.format("%s/admin/realms/%s/users", serverUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(adminToken);

        String fName = fullName != null && !fullName.isBlank() ? fullName.trim() : email;
        String lName = "User";
        if (fullName != null && fullName.contains(" ")) {
            int lastSpace = fullName.lastIndexOf(' ');
            fName = fullName.substring(0, lastSpace);
            lName = fullName.substring(lastSpace + 1);
        }

        Map<String, Object> userPayload = new HashMap<>();
        userPayload.put("username", email);
        userPayload.put("email", email);
        userPayload.put("enabled", enabled);
        userPayload.put("emailVerified", true);
        userPayload.put("firstName", fName);
        userPayload.put("lastName", lName);
        userPayload.put("requiredActions", Collections.emptyList());

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(userPayload, headers);

        String userId = null;
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(usersUrl, request, String.class);
            if (response.getStatusCode() == HttpStatus.CREATED) {
                String location = response.getHeaders().getFirst("Location");
                if (location != null) {
                    userId = location.substring(location.lastIndexOf('/') + 1);
                }
            }
        } catch (HttpClientErrorException.Conflict conflict) {
            log.info("User {} đã tồn tại trên Keycloak", email);
            userId = getKeycloakUserIdByEmail(email);
        } catch (Exception e) {
            log.error("Lỗi khi tạo user trên Keycloak: {}", e.getMessage());
        }

        if (userId == null) {
            userId = getKeycloakUserIdByEmail(email);
        }

        // Đặt mật khẩu chính thức (permanent) cho User trên Keycloak
        if (userId != null && password != null && !password.isBlank()) {
            resetUserPassword(email, password);
        }

        return userId;
    }

    /**
     * Tìm Keycloak User ID theo Email
     */
    public String getKeycloakUserIdByEmail(String email) {
        String adminToken = getAdminAccessToken();
        if (adminToken == null) return null;

        String searchUrl = String.format("%s/admin/realms/%s/users?username=%s&exact=true", serverUrl, realm, email);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(adminToken);
        HttpEntity<?> request = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(searchUrl, HttpMethod.GET, request, String.class);
            JsonNode array = objectMapper.readTree(response.getBody());
            if (array.isArray() && !array.isEmpty()) {
                return array.get(0).path("id").asText();
            }
        } catch (Exception e) {
            log.error("Lỗi tìm user trên Keycloak theo email {}: {}", email, e.getMessage());
        }
        return null;
    }

    /**
     * Cập nhật trạng thái Khóa / Mở khóa User trên Keycloak
     */
    public void updateUserStatus(String email, boolean enabled) {
        if (!isKeycloakEnabled()) return;

        String userId = getKeycloakUserIdByEmail(email);
        if (userId == null) {
            log.warn("Không tìm thấy user {} trên Keycloak để cập nhật trạng thái", email);
            return;
        }

        String userUrl = String.format("%s/admin/realms/%s/users/%s", serverUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());

        Map<String, Object> payload = new HashMap<>();
        payload.put("enabled", enabled);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

        try {
            restTemplate.exchange(userUrl, HttpMethod.PUT, request, Void.class);
            log.info("Đã cập nhật trạng thái enabled={} cho user {} trên Keycloak", enabled, email);
        } catch (Exception e) {
            log.error("Lỗi cập nhật trạng thái user {} trên Keycloak: {}", email, e.getMessage());
        }
    }

    /**
     * Reset mật khẩu User trên Keycloak
     */
    public void resetUserPassword(String email, String newPassword) {
        if (!isKeycloakEnabled()) return;

        String userId = getKeycloakUserIdByEmail(email);
        if (userId == null) {
            log.warn("Không tìm thấy user {} trên Keycloak để reset mật khẩu", email);
            return;
        }

        String resetUrl = String.format("%s/admin/realms/%s/users/%s/reset-password", serverUrl, realm, userId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(getAdminAccessToken());

        Map<String, Object> credential = new HashMap<>();
        credential.put("type", "password");
        credential.put("value", newPassword);
        credential.put("temporary", false);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(credential, headers);

        try {
            restTemplate.exchange(resetUrl, HttpMethod.PUT, request, Void.class);
            log.info("Đã reset mật khẩu cho user {} trên Keycloak thành công", email);
        } catch (Exception e) {
            log.error("Lỗi reset mật khẩu user {} trên Keycloak: {}", email, e.getMessage());
        }
    }

    /**
     * Xóa User trên Keycloak (khi xóa mềm)
     */
    public void deleteKeycloakUser(String email) {
        if (!isKeycloakEnabled()) return;

        // Có thể khóa tài khoản hoặc xóa khỏi Keycloak
        updateUserStatus(email, false);
    }

    /**
     * Đăng nhập trực tiếp lấy Token từ Keycloak (Direct Access Grants)
     */
    public Map<String, Object> loginDirect(String username, String password) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "password");
        map.add("client_id", clientId);
        map.add("username", username);
        map.add("password", password);
        map.add("scope", "openid profile email");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Đăng nhập Keycloak thất bại cho {}: {}", username, e.getMessage());
            return null;
        }
    }

    /**
     * Refresh Token từ Keycloak
     */
    public Map<String, Object> refreshKeycloakToken(String refreshToken) {
        String tokenUrl = String.format("%s/realms/%s/protocol/openid-connect/token", serverUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("grant_type", "refresh_token");
        map.add("client_id", clientId);
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);
            return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
        } catch (Exception e) {
            log.error("Làm mới token Keycloak thất bại: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Logout Session trên Keycloak
     */
    public void logoutKeycloak(String refreshToken) {
        if (!isKeycloakEnabled() || refreshToken == null || refreshToken.isBlank()) return;

        String logoutUrl = String.format("%s/realms/%s/protocol/openid-connect/logout", serverUrl, realm);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("client_id", clientId);
        map.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        try {
            restTemplate.postForEntity(logoutUrl, request, String.class);
            log.info("Đã gọi Keycloak logout thành công");
        } catch (Exception e) {
            log.error("Lỗi gọi Keycloak logout: {}", e.getMessage());
        }
    }
}
