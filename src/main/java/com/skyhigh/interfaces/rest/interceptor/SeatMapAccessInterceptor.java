package com.skyhigh.interfaces.rest.interceptor;

import com.skyhigh.application.abuse.AbuseDetectionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Component
@RequiredArgsConstructor
public class SeatMapAccessInterceptor implements HandlerInterceptor {

    private final AbuseDetectionService abuseDetectionService;

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        String path = request.getRequestURI();
        if (path.matches(".*/flights/\\d+/seats") && "GET".equals(request.getMethod()) && response.getStatus() == 200) {
            String sourceId = getSourceId(request);
            Long flightId = extractFlightId(path);
            if (flightId != null) {
                abuseDetectionService.recordAccess(sourceId, flightId, path);
            }
        }
    }

    private String getSourceId(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private Long extractFlightId(String path) {
        try {
            String[] parts = path.split("/");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("flights".equals(parts[i]) && i + 1 < parts.length) {
                    return Long.parseLong(parts[i + 1]);
                }
            }
        } catch (NumberFormatException ignored) {
        }
        return null;
    }
}
