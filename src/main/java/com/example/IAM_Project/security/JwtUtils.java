package com.example.IAM_Project.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

@Component
public class JwtUtils {

    private static final Logger logger = LoggerFactory.getLogger(JwtUtils.class);

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration:900000}")
    private long jwtExpirationMs;

    @Value("${jwt.refresh-expiration:604800000}")
    private long jwtRefreshExpirationMs;

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateAccessToken(String email, Long userId) {
        String jwtId = UUID.randomUUID().toString();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationMs);

        return Jwts.builder()
                .id(jwtId)
                .subject(email)
                .claim("userId", userId)
                .claim("tokenType", "ACCESS")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String generateRefreshToken(String email, Long userId, String jwtId) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationMs);

        return Jwts.builder()
                .id(jwtId)
                .subject(email)
                .claim("userId", userId)
                .claim("tokenType", "REFRESH")
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(getSigningKey())
                .compact();
    }

    public String getUsernameFromJwtToken(String token) {
        return getClaimFromToken(token, Claims::getSubject);
    }

    public String getJwtIdFromToken(String token) {
        return getClaimFromToken(token, Claims::getId);
    }

    public Long getUserIdFromToken(String token) {
        Claims claims = getAllClaimsFromToken(token);
        Object userId = claims.get("userId");
        if (userId instanceof Number) {
            return ((Number) userId).longValue();
        }
        return null;
    }

    public Date getExpirationDateFromToken(String token) {
        return getClaimFromToken(token, Claims::getExpiration);
    }

    public long getExpirationMs() {
        return jwtExpirationMs;
    }

    public long getRefreshExpirationMs() {
        return jwtRefreshExpirationMs;
    }

    public <T> T getClaimFromToken(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = getAllClaimsFromToken(token);
        return claimsResolver.apply(claims);
    }

    public Claims getAllClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            logger.debug("Local JWT validation failed: {}", e.getMessage());
        }
        return false;
    }

    /**
     * Kiểm tra token có phải từ Keycloak hay không
     */
    public boolean isKeycloakToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String header = new String(java.util.Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
                String payload = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                return header.contains("RS256") || payload.contains("realms/") || payload.contains("keycloak") || payload.contains("azp");
            }
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * Xác thực và trích xuất email/username từ token Keycloak
     */
    public String extractUsernameFromKeycloakToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length >= 2) {
                String payloadJson = new String(java.util.Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
                com.fasterxml.jackson.databind.JsonNode node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(payloadJson);
                
                // Kiểm tra hết hạn (exp)
                long exp = node.path("exp").asLong(0);
                if (exp > 0 && exp < (System.currentTimeMillis() / 1000L)) {
                    logger.warn("Keycloak token has expired (exp: {})", exp);
                    return null;
                }

                if (node.hasNonNull("email")) {
                    return node.get("email").asText();
                } else if (node.hasNonNull("preferred_username")) {
                    return node.get("preferred_username").asText();
                } else if (node.hasNonNull("sub")) {
                    return node.get("sub").asText();
                }
            }
        } catch (Exception e) {
            logger.error("Lỗi giải mã token Keycloak: {}", e.getMessage());
        }
        return null;
    }
}
