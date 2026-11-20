package com.example.IAM_Project.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class RedisService {

    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);
    private final StringRedisTemplate redisTemplate;

    private static final String BLACKLIST_PREFIX = "iam:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX = "iam:refresh:";
    private static final String OTP_PREFIX = "iam:otp:";

    public void addToBlacklist(String jwtId, long ttlMs) {
        if (jwtId == null || ttlMs <= 0) return;
        try {
            String key = BLACKLIST_PREFIX + jwtId;
            redisTemplate.opsForValue().set(key, "REVOKED", ttlMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("Failed to add token to Redis blacklist: {}", e.getMessage());
        }
    }

    public boolean isBlacklisted(String jwtId) {
        if (jwtId == null) return false;
        try {
            String key = BLACKLIST_PREFIX + jwtId;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check token in Redis blacklist: {}", e.getMessage());
            return false;
        }
    }

    public void storeRefreshToken(String token, String email, long ttlMs) {
        try {
            String key = REFRESH_TOKEN_PREFIX + token;
            redisTemplate.opsForValue().set(key, email, ttlMs, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            logger.error("Failed to store refresh token in Redis: {}", e.getMessage());
        }
    }

    public boolean hasRefreshToken(String token) {
        try {
            String key = REFRESH_TOKEN_PREFIX + token;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            logger.error("Failed to check refresh token in Redis: {}", e.getMessage());
            return false;
        }
    }

    public void deleteRefreshToken(String token) {
        try {
            String key = REFRESH_TOKEN_PREFIX + token;
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Failed to delete refresh token from Redis: {}", e.getMessage());
        }
    }

    public void storeOtp(String email, String otp, long expirationMinutes) {
        try {
            String cleanOtp = otp.trim();
            String key = OTP_PREFIX + email;
            redisTemplate.opsForValue().set(key, cleanOtp, expirationMinutes, TimeUnit.MINUTES);
        } catch (Exception e) {
            logger.error("Failed to store OTP in Redis: {}", e.getMessage());
        }
    }

    public String getOtp(String email) {
        try {
            String key = OTP_PREFIX + email;
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            logger.error("Failed to get OTP from Redis: {}", e.getMessage());
            return null;
        }
    }

    public void deleteOtp(String email) {
        try {
            String key = OTP_PREFIX + email;
            redisTemplate.delete(key);
        } catch (Exception e) {
            logger.error("Failed to delete OTP from Redis: {}", e.getMessage());
        }
    }
}
