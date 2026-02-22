package com.skyhigh.infrastructure.config;

import com.skyhigh.interfaces.rest.interceptor.SeatMapAccessInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final SeatMapAccessInterceptor seatMapAccessInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(seatMapAccessInterceptor)
                .addPathPatterns("/flights/*/seats");
    }
}
