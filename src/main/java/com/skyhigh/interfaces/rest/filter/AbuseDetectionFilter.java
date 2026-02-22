package com.skyhigh.interfaces.rest.filter;

import com.skyhigh.application.abuse.AbuseDetectionService;
import com.skyhigh.application.abuse.AbuseDetectedException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Order(1)
@RequiredArgsConstructor
public class AbuseDetectionFilter extends OncePerRequestFilter {

    private final AbuseDetectionService abuseDetectionService;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.contains("/seats") || !"GET".equals(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String sourceId = getSourceId(request);

        try {
            abuseDetectionService.checkAbuse(sourceId);
            filterChain.doFilter(request, response);
        } catch (AbuseDetectedException e) {
            response.setStatus(429);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"Too Many Requests\",\"message\":\"" + e.getMessage() + "\"}");
        }
    }

    private String getSourceId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
