package com.example.IAM_Project.security;

import com.example.IAM_Project.repository.TokenBlacklistRepository;
import com.example.IAM_Project.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtUtils jwtUtils;
    private final CustomUserDetailsService userDetailsService;
    private final RedisService redisService;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = parseJwt(request);
            if (jwt != null) {
                String username = null;

                if (jwtUtils.isKeycloakToken(jwt)) {
                    // Xử lý Bearer Token từ Keycloak
                    username = jwtUtils.extractUsernameFromKeycloakToken(jwt);
                } else if (jwtUtils.validateJwtToken(jwt)) {
                    // Xử lý Token nội bộ Self-IDP
                    String jwtId = jwtUtils.getJwtIdFromToken(jwt);
                    boolean isBlacklisted = redisService.isBlacklisted(jwtId) || tokenBlacklistRepository.existsByJwtId(jwtId);
                    if (isBlacklisted) {
                        logger.warn("Token with jwtId {} is blacklisted", jwtId);
                        filterChain.doFilter(request, response);
                        return;
                    }
                    username = jwtUtils.getUsernameFromJwtToken(jwt);
                }

                // Đề bài yêu cầu: "mỗi request kiểm tra thông tin user trong database của iam, nếu database của iam có thì mới cho thực hiện request"
                if (username != null) {
                    try {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                        if (userDetails != null && userDetails.isEnabled() && userDetails.isAccountNonLocked()) {
                            UsernamePasswordAuthenticationToken authentication =
                                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                            SecurityContextHolder.getContext().setAuthentication(authentication);
                        } else {
                            logger.warn("User {} trong DB bị vô hiệu hóa hoặc khóa!", username);
                        }
                    } catch (Exception notFound) {
                        logger.warn("User {} từ token không tồn tại trong database nội bộ IAM!", username);
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Cannot set user authentication: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String parseJwt(HttpServletRequest request) {
        String headerAuth = request.getHeader("Authorization");
        if (StringUtils.hasText(headerAuth) && headerAuth.startsWith("Bearer ")) {
            return headerAuth.substring(7);
        }
        return null;
    }
}
