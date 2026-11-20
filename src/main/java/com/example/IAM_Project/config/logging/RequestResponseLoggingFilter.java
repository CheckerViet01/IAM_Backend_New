package com.example.IAM_Project.config.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestResponseLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestResponseLoggingFilter.class);

    private static final Pattern SENSITIVE_DATA_PATTERN = Pattern.compile(
            "\"(password|newPassword|confirmPassword|currentPassword|clientSecret|secret|token)\"\\s*:\\s*\"[^\"]*\"",
            Pattern.CASE_INSENSITIVE
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/swagger-ui") 
                || path.startsWith("/v3/api-docs") 
                || path.startsWith("/actuator")
                || path.startsWith("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, 1024 * 1024);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        long startTime = System.currentTimeMillis();
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String queryString = request.getQueryString() != null ? "?" + request.getQueryString() : "";
        String clientIp = getClientIp(request);

        log.info("[HTTP-IN] ==> {} {}{} from IP: {}", method, uri, queryString, clientIp);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } catch (Exception ex) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[HTTP-EXCEPTION] <== {} {}{} failed in {} ms: {}", method, uri, queryString, duration, ex.getMessage(), ex);
            throw ex;
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            int status = responseWrapper.getStatus();

            // Mask request body
            String requestBody = getSanitizedBody(requestWrapper.getContentAsByteArray());
            if (!requestBody.isBlank()) {
                log.debug("[HTTP-REQ-BODY] Payload: {}", requestBody);
            }

            // Mask response body if text/json
            String responseBody = getSanitizedBody(responseWrapper.getContentAsByteArray());
            if (!responseBody.isBlank() && responseWrapper.getContentType() != null && responseWrapper.getContentType().contains("json")) {
                log.debug("[HTTP-RES-BODY] Payload: {}", responseBody);
            }

            log.info("[HTTP-OUT] <== {} {}{} Status: {} ({} ms)", method, uri, queryString, status, duration);

            responseWrapper.copyBodyToResponse();
        }
    }

    private String getSanitizedBody(byte[] content) {
        if (content == null || content.length == 0) {
            return "";
        }
        try {
            String raw = new String(content, StandardCharsets.UTF_8);
            if (raw.length() > 2000) {
                raw = raw.substring(0, 2000) + "... [truncated]";
            }
            return SENSITIVE_DATA_PATTERN.matcher(raw).replaceAll("\"$1\":\"***MASKED***\"");
        } catch (Exception e) {
            return "[Binary or unreadable body]";
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        if (xfHeader == null || xfHeader.isEmpty() || "unknown".equalsIgnoreCase(xfHeader)) {
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0].trim();
    }
}
