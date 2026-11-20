package com.example.IAM_Project.service;

import com.example.IAM_Project.entity.User;
import com.example.IAM_Project.entity.UserActivityLog;
import com.example.IAM_Project.repository.UserActivityLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ActivityLogService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityLogService.class);
    private final UserActivityLogRepository activityLogRepository;

    public void log(User user, String emailAttempted, String action, String status, HttpServletRequest request, String details) {
        try {
            String ipAddress = null;
            String userAgent = null;

            if (request != null) {
                ipAddress = request.getHeader("X-Forwarded-For");
                if (ipAddress == null || ipAddress.isEmpty() || "unknown".equalsIgnoreCase(ipAddress)) {
                    ipAddress = request.getRemoteAddr();
                } else if (ipAddress.contains(",")) {
                    ipAddress = ipAddress.split(",")[0].trim();
                }
                userAgent = request.getHeader("User-Agent");
                if (userAgent != null && userAgent.length() > 990) {
                    userAgent = userAgent.substring(0, 990);
                }
            }

            if (details != null && details.length() > 1990) {
                details = details.substring(0, 1990);
            }

            UserActivityLog log = UserActivityLog.builder()
                    .user(user)
                    .emailAttempted(emailAttempted != null ? emailAttempted : (user != null ? user.getEmail() : null))
                    .action(action)
                    .status(status)
                    .ipAddress(ipAddress)
                    .userAgent(userAgent)
                    .details(details)
                    .createdAt(LocalDateTime.now())
                    .build();

            activityLogRepository.save(log);
        } catch (Exception e) {
            logger.error("Failed to write user activity log: {}", e.getMessage());
        }
    }
}
